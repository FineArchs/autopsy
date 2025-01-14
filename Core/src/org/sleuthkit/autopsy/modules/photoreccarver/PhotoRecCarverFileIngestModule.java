/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.modules.photoreccarver;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ProcTerminationCode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * A file ingest module that runs the Unallocated Carver executable with
 * unallocated space files as input.
 */
@NbBundle.Messages({
    "PhotoRecIngestModule.PermissionsNotSufficient=Insufficient permissions accessing",
    "PhotoRecIngestModule.PermissionsNotSufficientSeeReference=See 'Shared Drive Authentication' in Autopsy help.",
    "# {0} - output directory name", "cannotCreateOutputDir.message=Unable to create output directory: {0}.",
    "unallocatedSpaceProcessingSettingsError.message=The selected file ingest filter ignores unallocated space. This module carves unallocated space. Please choose a filter which does not ignore unallocated space or disable this module.",
    "unsupportedOS.message=PhotoRec module is supported on Windows platforms only.",
    "missingExecutable.message=Unable to locate PhotoRec executable.",
    "cannotRunExecutable.message=Unable to execute PhotoRec.",
    "PhotoRecIngestModule.nonHostnameUNCPathUsed=PhotoRec cannot operate with a UNC path containing IP addresses."
})
final class PhotoRecCarverFileIngestModule implements FileIngestModule {

    static final boolean DEFAULT_CONFIG_KEEP_CORRUPTED_FILES = false;
    static final PhotoRecCarverIngestJobSettings.ExtensionFilterOption DEFAULT_CONFIG_EXTENSION_FILTER
            = PhotoRecCarverIngestJobSettings.ExtensionFilterOption.NO_FILTER;

    static final boolean DEFAULT_CONFIG_INCLUDE_ELSE_EXCLUDE = false;

    private static final String PHOTOREC_TEMP_SUBDIR = "PhotoRec Carver"; // NON-NLS Note that we need the space in this dir name (JIRA-6878)
    private static final String PHOTOREC_DIRECTORY = "photorec_exec"; //NON-NLS
    private static final String PHOTOREC_SUBDIRECTORY = "bin"; //NON-NLS
    private static final String PHOTOREC_EXECUTABLE = "photorec_win.exe"; //NON-NLS
    private static final String PHOTOREC_LINUX_EXECUTABLE = "photorec";
    private static final String PHOTOREC_RESULTS_BASE = "results"; //NON-NLS
    private static final String PHOTOREC_RESULTS_EXTENDED = "results.1"; //NON-NLS
    private static final String PHOTOREC_REPORT = "report.xml"; //NON-NLS
    private static final String LOG_FILE = "run_log.txt"; //NON-NLS
    private static final String SEP = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(PhotoRecCarverFileIngestModule.class.getName());
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final Map<Long, WorkingPaths> pathsByJob = new ConcurrentHashMap<>();
    private IngestJobContext context;
    private Path rootOutputDirPath;
    private Path rootTempDirPath;
    private File executableFile;
    private IngestServices services;
    private final UNCPathUtilities uncPathUtilities = new UNCPathUtilities();
    private final PhotoRecCarverIngestJobSettings settings;
    private String optionsString;
    private long jobId;

    private static class IngestJobTotals {

        private final AtomicLong totalItemsRecovered = new AtomicLong(0);
        private final AtomicLong totalItemsWithErrors = new AtomicLong(0);
        private final AtomicLong totalWritetime = new AtomicLong(0);
        private final AtomicLong totalParsetime = new AtomicLong(0);
    }

    /**
     * Create a PhotoRec Carver ingest module instance.
     *
     * @param settings Ingest job settings used to configure the module.
     */
    PhotoRecCarverFileIngestModule(PhotoRecCarverIngestJobSettings settings) {
        this.settings = settings;
    }

    /**
     * Creates a photorec command line options string based on the settings.
     *
     * @param settings The settings.
     *
     * @return The options string to be provided to Photorec on the command
     *         line.
     */
    private String getPhotorecOptions(PhotoRecCarverIngestJobSettings settings) {
        List<String> toRet = new ArrayList<String>();

        if (settings.isKeepCorruptedFiles()) {
            toRet.addAll(Arrays.asList("options", "keep_corrupted_file"));
        }

        if (settings.getExtensionFilterOption()
                != PhotoRecCarverIngestJobSettings.ExtensionFilterOption.NO_FILTER) {

            // add the file opt menu item
            toRet.add("fileopt");

            String enable = "enable";
            String disable = "disable";

            // if we are including file extensions, then we are excluding 
            // everything else and vice-versa.
            String everythingEnable = settings.getExtensionFilterOption()
                    == PhotoRecCarverIngestJobSettings.ExtensionFilterOption.INCLUDE
                            ? disable : enable;

            toRet.addAll(Arrays.asList("everything", everythingEnable));

            final String itemEnable = settings.getExtensionFilterOption()
                    == PhotoRecCarverIngestJobSettings.ExtensionFilterOption.INCLUDE
                            ? enable : disable;

            settings.getExtensions().forEach((extension) -> {
                toRet.addAll(Arrays.asList(extension, itemEnable));
            });
        }

        toRet.add("search");
        return String.join(",", toRet);
    }

    private static synchronized IngestJobTotals getTotalsForIngestJobs(long ingestJobId) {
        IngestJobTotals totals = totalsForIngestJobs.get(ingestJobId);
        if (totals == null) {
            totals = new PhotoRecCarverFileIngestModule.IngestJobTotals();
            totalsForIngestJobs.put(ingestJobId, totals);
        }
        return totals;
    }

    private static synchronized void initTotalsForIngestJob(long ingestJobId) {
        IngestJobTotals totals = new PhotoRecCarverFileIngestModule.IngestJobTotals();
        totalsForIngestJobs.put(ingestJobId, totals);
    }

    /**
     * @inheritDoc
     */
    @Override
    @NbBundle.Messages({
        "# {0} - extensions",
        "PhotoRecCarverFileIngestModule_startUp_invalidExtensions_description=The following extensions are invalid: {0}",
        "PhotoRecCarverFileIngestModule_startUp_noExtensionsProvided_description=No extensions provided for PhotoRec to carve."
    })
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        // validate settings
        if (this.settings.getExtensionFilterOption() != PhotoRecCarverIngestJobSettings.ExtensionFilterOption.NO_FILTER) {
            if (this.settings.getExtensions().isEmpty()
                    && this.settings.getExtensionFilterOption() == PhotoRecCarverIngestJobSettings.ExtensionFilterOption.INCLUDE) {

                throw new IngestModule.IngestModuleException(
                        Bundle.PhotoRecCarverFileIngestModule_startUp_noExtensionsProvided_description());
            }

            List<String> invalidExtensions = this.settings.getExtensions().stream()
                    .filter((ext) -> !PhotoRecCarverFileOptExtensions.isValidExtension(ext))
                    .collect(Collectors.toList());

            if (!invalidExtensions.isEmpty()) {
                throw new IngestModule.IngestModuleException(
                        Bundle.PhotoRecCarverFileIngestModule_startUp_invalidExtensions_description(
                                String.join(",", invalidExtensions)));
            }
        }

        this.optionsString = getPhotorecOptions(this.settings);

        this.context = context;
        this.services = IngestServices.getInstance();
        this.jobId = this.context.getJobId();

        // If the global unallocated space processing setting and the module
        // process unallocated space only setting are not in sych, throw an 
        // exception. Although the result would not be incorrect, it would be
        // unfortunate for the user to get an accidental no-op for this module. 
        if (!this.context.processingUnallocatedSpace()) {
            throw new IngestModule.IngestModuleException(Bundle.unallocatedSpaceProcessingSettingsError_message());
        }

        this.rootOutputDirPath = createModuleOutputDirectoryForCase();
        this.rootTempDirPath = createTempOutputDirectoryForCase();

        //Set photorec executable directory based on operating system.
        executableFile = locateExecutable();

        if (PhotoRecCarverFileIngestModule.refCounter.incrementAndGet(this.jobId) == 1) {
            try {
                // The first instance creates an output subdirectory with a date and time stamp
                DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");  // NON-NLS
                Date date = new Date();
                String folder = this.context.getDataSource().getId() + "_" + dateFormat.format(date);
                Path outputDirPath = Paths.get(this.rootOutputDirPath.toAbsolutePath().toString(), folder);
                Files.createDirectories(outputDirPath);

                // A temp subdirectory is also created as a location for writing unallocated space files to disk.
                Path tempDirPath = Paths.get(this.rootTempDirPath.toString(), folder);
                Files.createDirectory(tempDirPath);

                // Save the directories for the current job.
                PhotoRecCarverFileIngestModule.pathsByJob.put(this.jobId, new WorkingPaths(outputDirPath, tempDirPath));

                // Initialize job totals
                initTotalsForIngestJob(jobId);
            } catch (SecurityException | IOException | UnsupportedOperationException ex) {
                throw new IngestModule.IngestModuleException(Bundle.cannotCreateOutputDir_message(ex.getLocalizedMessage()), ex);
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModule.ProcessResult process(AbstractFile file) {
        // Skip everything except unallocated space files.
        if (file.getType() != TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return IngestModule.ProcessResult.OK;
        }

        // Safely get a reference to the totalsForIngestJobs object
        IngestJobTotals totals = getTotalsForIngestJobs(jobId);

        Path tempFilePath = null;
        try {
            // Verify initialization succeeded.
            if (null == this.executableFile) {
                logger.log(Level.SEVERE, "PhotoRec carver called after failed start up");  // NON-NLS
                return IngestModule.ProcessResult.ERROR;
            }

            // Check that we have roughly enough disk space left to complete the operation
            // Some network drives always return -1 for free disk space. 
            // In this case, expect enough space and move on.
            long freeDiskSpace = IngestServices.getInstance().getFreeDiskSpace();
            if ((freeDiskSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN) && ((file.getSize() * 1.2) > freeDiskSpace)) {
                logger.log(Level.SEVERE, "PhotoRec error processing {0} with {1} Not enough space on primary disk to save unallocated space.", // NON-NLS
                        new Object[]{file.getName(), PhotoRecCarverIngestModuleFactory.getModuleName()}); // NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "PhotoRecIngestModule.UnableToCarve", file.getName()),
                        NbBundle.getMessage(this.getClass(), "PhotoRecIngestModule.NotEnoughDiskSpace"));
                return IngestModule.ProcessResult.ERROR;
            }
            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                return IngestModule.ProcessResult.OK;
            }

            // Write the file to disk.
            long writestart = System.currentTimeMillis();
            WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.get(this.jobId);
            tempFilePath = Paths.get(paths.getTempDirPath().toString(), file.getName());
            ContentUtils.writeToFile(file, tempFilePath.toFile(), context::fileIngestIsCancelled);

            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                return IngestModule.ProcessResult.OK;
            }

            // Create a subdirectory for this file.
            Path outputDirPath = Paths.get(paths.getOutputDirPath().toString(), file.getName());
            Files.createDirectory(outputDirPath);
            File log = new File(Paths.get(outputDirPath.toString(), LOG_FILE).toString()); //NON-NLS

            // Scan the file with Unallocated Carver.
            ProcessBuilder processAndSettings = new ProcessBuilder(
                    executableFile.toString(),
                    "/d", // NON-NLS
                    outputDirPath.toAbsolutePath().toString() + File.separator + PHOTOREC_RESULTS_BASE,
                    "/cmd", // NON-NLS
                    tempFilePath.toFile().toString());

            processAndSettings.command().add(this.optionsString);

            // Add environment variable to force PhotoRec to run with the same permissions Autopsy uses
            processAndSettings.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
            processAndSettings.redirectErrorStream(true);
            processAndSettings.redirectOutput(Redirect.appendTo(log));

            FileIngestModuleProcessTerminator terminator = new FileIngestModuleProcessTerminator(this.context, true);
            int exitValue = ExecUtil.execute(processAndSettings, terminator);

            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                cleanup(outputDirPath, tempFilePath);
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                return IngestModule.ProcessResult.OK;
            } else if (terminator.getTerminationCode() == ProcTerminationCode.TIME_OUT) {
                cleanup(outputDirPath, tempFilePath);
                String msg = NbBundle.getMessage(this.getClass(), "PhotoRecIngestModule.processTerminated") + file.getName(); // NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "PhotoRecIngestModule.moduleError"), msg); // NON-NLS                
                logger.log(Level.SEVERE, msg);
                return IngestModule.ProcessResult.ERROR;
            } else if (0 != exitValue) {
                // if it failed or was cancelled by timeout, result is ERROR
                cleanup(outputDirPath, tempFilePath);
                totals.totalItemsWithErrors.incrementAndGet();
                logger.log(Level.SEVERE, "PhotoRec carver returned error exit value = {0} when scanning {1}", // NON-NLS
                        new Object[]{exitValue, file.getName()}); // NON-NLS
                MessageNotifyUtil.Notify.error(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.error.exitValue", // NON-NLS
                        new Object[]{exitValue, file.getName()}));
                return IngestModule.ProcessResult.ERROR;
            }

            // Move carver log file to avoid placement into Autopsy results. PhotoRec appends ".1" to the folder name.
            java.io.File oldAuditFile = new java.io.File(Paths.get(outputDirPath.toString(), PHOTOREC_RESULTS_EXTENDED, PHOTOREC_REPORT).toString()); //NON-NLS
            java.io.File newAuditFile = new java.io.File(Paths.get(outputDirPath.toString(), PHOTOREC_REPORT).toString()); //NON-NLS
            oldAuditFile.renameTo(newAuditFile);

            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                return IngestModule.ProcessResult.OK;
            }
            Path pathToRemove = Paths.get(outputDirPath.toAbsolutePath().toString());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(pathToRemove)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        FileUtil.deleteDir(new File(entry.toString()));
                    }
                }
            }
            long writedelta = (System.currentTimeMillis() - writestart);
            totals.totalWritetime.addAndGet(writedelta);

            // Now that we've cleaned up the folders and data files, parse the xml output file to add carved items into the database
            long calcstart = System.currentTimeMillis();
            PhotoRecCarverOutputParser parser = new PhotoRecCarverOutputParser(outputDirPath);
            if (this.context.fileIngestIsCancelled() == true) {
                // if it was cancelled by the user, result is OK
                logger.log(Level.INFO, "PhotoRec cancelled by user"); // NON-NLS
                MessageNotifyUtil.Notify.info(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.cancelledByUser"));
                return IngestModule.ProcessResult.OK;
            }
            List<LayoutFile> carvedItems = parser.parse(newAuditFile, file, context);
            long calcdelta = (System.currentTimeMillis() - calcstart);
            totals.totalParsetime.addAndGet(calcdelta);
            if (carvedItems != null && !carvedItems.isEmpty()) { // if there were any results from carving, add the unallocated carving event to the reports list.
                totals.totalItemsRecovered.addAndGet(carvedItems.size());
                context.addFilesToJob(new ArrayList<>(carvedItems));
                
                // Fire events for all virtual directory parents that may have just been created
                try {
                    List<AbstractFile> virtualParentDirs = getVirtualDirectoryParents(carvedItems);
                    for (AbstractFile virtualDir : virtualParentDirs) {
                        services.fireModuleContentEvent(new ModuleContentEvent(virtualDir));
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error collecting carved file parent directories", ex);
                }
                services.fireModuleContentEvent(new ModuleContentEvent(carvedItems.get(0))); // fire an event to update the tree
            }
        } catch (ReadContentInputStreamException ex) {
            totals.totalItemsWithErrors.incrementAndGet();
            logger.log(Level.WARNING, String.format("Error reading file '%s' (id=%d) with the PhotoRec carver.", file.getName(), file.getId()), ex); // NON-NLS
            MessageNotifyUtil.Notify.error(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.error.msg", file.getName()));
            return IngestModule.ProcessResult.ERROR;
        } catch (IOException ex) {
            totals.totalItemsWithErrors.incrementAndGet();
            logger.log(Level.SEVERE, String.format("Error writing or processing file '%s' (id=%d) to '%s' with the PhotoRec carver.", file.getName(), file.getId(), tempFilePath), ex); // NON-NLS
            MessageNotifyUtil.Notify.error(PhotoRecCarverIngestModuleFactory.getModuleName(), NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.error.msg", file.getName()));
            return IngestModule.ProcessResult.ERROR;
        } finally {
            if (null != tempFilePath && Files.exists(tempFilePath)) {
                // Get rid of the unallocated space file.
                tempFilePath.toFile().delete();
            }
        }
        return IngestModule.ProcessResult.OK;

    }
    
    /**
     * Return a list of all virtual directory parents of the list of carved files.
     * These directories were likely just created while adding the carved files to the
     * case database. The directories will include the main "$CarvedFiles" folder and
     * any subfolders.
     * 
     * @param layoutFiles List of carved files.
     * 
     * @return List of virtual directory parents of the carved files.
     * 
     * @throws TskCoreException 
     */
    private List<AbstractFile> getVirtualDirectoryParents(List<LayoutFile> layoutFiles) throws TskCoreException {
        // Keep track of which parent IDs we've already looked at to avoid unneccessary database lookups
        Set<Long> processedParentIds = new HashSet<>();
        
        // For each layout file, go up its parent structure until we:
        // - Find a parent that we've already looked at
        // - Find a parent that is not a normal virtual directory
        // Add all new parents to the list.
        List<AbstractFile> parentFiles = new ArrayList<>();
        for (LayoutFile file : layoutFiles) {
            AbstractFile currentFile = file;
            while (currentFile.getParentId().isPresent() && !processedParentIds.contains(currentFile.getParentId().get())) {
                Content parent = currentFile.getParent();
                processedParentIds.add(parent.getId());
                if (! (parent instanceof VirtualDirectory)
                        || (currentFile instanceof DataSource)) {
                    // Stop if we hit a non-virtual directory
                    break;
                }
                
                // Move up to the next level and save the current virtual directory to the list.
                currentFile = (AbstractFile)parent;
                parentFiles.add(currentFile);
            }
        }
        return parentFiles;
    }

    private void cleanup(Path outputDirPath, Path tempFilePath) {
        // cleanup the output path
        FileUtil.deleteDir(new File(outputDirPath.toString()));
        if (null != tempFilePath && Files.exists(tempFilePath)) {
            tempFilePath.toFile().delete();
        }
    }

    private static synchronized void postSummary(long jobId) {
        IngestJobTotals jobTotals = totalsForIngestJobs.remove(jobId);

        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS

        detailsSb.append("<tr><td>") //NON-NLS
                .append(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.complete.numberOfCarved"))
                .append("</td>"); //NON-NLS
        detailsSb.append("<td>").append(jobTotals.totalItemsRecovered.get()).append("</td></tr>"); //NON-NLS

        detailsSb.append("<tr><td>") //NON-NLS
                .append(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.complete.numberOfErrors"))
                .append("</td>"); //NON-NLS
        detailsSb.append("<td>").append(jobTotals.totalItemsWithErrors.get()).append("</td></tr>"); //NON-NLS

        detailsSb.append("<tr><td>") //NON-NLS
                .append(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.complete.totalWritetime"))
                .append("</td><td>").append(jobTotals.totalWritetime.get()).append("</td></tr>\n"); //NON-NLS
        detailsSb.append("<tr><td>") //NON-NLS
                .append(NbBundle.getMessage(PhotoRecCarverFileIngestModule.class, "PhotoRecIngestModule.complete.totalParsetime"))
                .append("</td><td>").append(jobTotals.totalParsetime.get()).append("</td></tr>\n"); //NON-NLS
        detailsSb.append("</table>"); //NON-NLS

        IngestServices.getInstance().postMessage(IngestMessage.createMessage(
                IngestMessage.MessageType.INFO,
                PhotoRecCarverIngestModuleFactory.getModuleName(),
                NbBundle.getMessage(PhotoRecCarverFileIngestModule.class,
                        "PhotoRecIngestModule.complete.photoRecResults"),
                detailsSb.toString()));

    }

    /**
     * @inheritDoc
     */
    @Override
    public void shutDown() {
        if (this.context != null && refCounter.decrementAndGet(this.jobId) == 0) {
            try {
                // The last instance of this module for an ingest job cleans out 
                // the working paths map entry for the job and deletes the temp dir.
                WorkingPaths paths = PhotoRecCarverFileIngestModule.pathsByJob.remove(this.jobId);
                FileUtil.deleteDir(new File(paths.getTempDirPath().toString()));
                postSummary(jobId);
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Error shutting down PhotoRec carver module", ex); // NON-NLS
            }
        }
    }

    private static final class WorkingPaths {

        private final Path outputDirPath;
        private final Path tempDirPath;

        WorkingPaths(Path outputDirPath, Path tempDirPath) {
            this.outputDirPath = outputDirPath;
            this.tempDirPath = tempDirPath;
        }

        Path getOutputDirPath() {
            return this.outputDirPath;
        }

        Path getTempDirPath() {
            return this.tempDirPath;
        }
    }

    /**
     * Creates the output directory for this module for the current case's temp
     * directory, if it does not already exist.
     *
     * @return The absolute path of the output directory.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    synchronized Path createTempOutputDirectoryForCase() throws IngestModule.IngestModuleException {
        try {
            Path path = Paths.get(Case.getCurrentCaseThrows().getTempDirectory(), PHOTOREC_TEMP_SUBDIR);
            return createOutputDirectoryForCase(path);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException(Bundle.cannotCreateOutputDir_message(ex.getLocalizedMessage()), ex);
        }
    }

    /**
     * Creates the output directory for this module for the current case's
     * module directory, if it does not already exist.
     *
     * @return The absolute path of the output directory.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    synchronized Path createModuleOutputDirectoryForCase() throws IngestModule.IngestModuleException {
        try {
            Path path = Paths.get(Case.getCurrentCaseThrows().getModuleDirectory(), PhotoRecCarverIngestModuleFactory.getModuleName());
            return createOutputDirectoryForCase(path);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException(Bundle.cannotCreateOutputDir_message(ex.getLocalizedMessage()), ex);
        }
    }

    /**
     * Creates the output directory for this module for the current case, if it
     * does not already exist.
     *
     * @param providedPath The absolute path to be created.
     *
     * @return The absolute path of the output directory.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    private synchronized Path createOutputDirectoryForCase(Path providedPath) throws IngestModule.IngestModuleException {
        Path path = providedPath;
        try {
            Files.createDirectory(path);
            if (UNCPathUtilities.isUNC(path)) {
                // if the UNC path is using an IP address, convert to hostname
                path = uncPathUtilities.ipToHostName(path);
                if (path == null) {
                    throw new IngestModule.IngestModuleException(Bundle.PhotoRecIngestModule_nonHostnameUNCPathUsed());
                }
                if (false == FileUtil.hasReadWriteAccess(path)) {
                    throw new IngestModule.IngestModuleException(
                            Bundle.PhotoRecIngestModule_PermissionsNotSufficient() + SEP + path.toString() + SEP
                            + Bundle.PhotoRecIngestModule_PermissionsNotSufficientSeeReference()
                    );
                }
            }
        } catch (FileAlreadyExistsException ex) {
            // No worries.
        } catch (IOException | SecurityException | UnsupportedOperationException ex) {
            throw new IngestModule.IngestModuleException(Bundle.cannotCreateOutputDir_message(ex.getLocalizedMessage()), ex);
        }
        return path;
    }

    /**
     * Finds and returns the path to the executable, if able.
     *
     * @param executableToFindName The name of the executable to find
     *
     * @return A File reference or throws an exception
     *
     * @throws IngestModuleException
     */
    public static File locateExecutable() throws IngestModule.IngestModuleException {
        File exeFile;
        if (PlatformUtil.isWindowsOS()) {
            Path execName = Paths.get(PHOTOREC_DIRECTORY, PHOTOREC_SUBDIRECTORY, PHOTOREC_EXECUTABLE);
            exeFile = InstalledFileLocator.getDefault().locate(execName.toString(), PhotoRecCarverFileIngestModule.class.getPackage().getName(), false);
        } else {
            exeFile = null;
            for (String dirName: System.getenv("PATH").split(File.pathSeparator)) {
                File testExe = new File(dirName, PHOTOREC_LINUX_EXECUTABLE);
                if (testExe.exists()) {
                    exeFile = testExe;
                    break;
                }
            }
        }

        if (null == exeFile) {
            throw new IngestModule.IngestModuleException(Bundle.missingExecutable_message());
        }

        if (!exeFile.canExecute()) {
            throw new IngestModule.IngestModuleException(Bundle.cannotRunExecutable_message());
        }

        return exeFile;
    }

}
