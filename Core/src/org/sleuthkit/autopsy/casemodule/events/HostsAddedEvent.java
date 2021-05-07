/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Host;

/**
 * Application events published when hosts have been added to the Sleuth Kit
 * data model for a case.
 */
public final class HostsAddedEvent extends HostsEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an application event published when hosts have been added to
     * the Sleuth Kit data model for a case.
     *
     * @param persons The hosts that have been added.
     */
    public HostsAddedEvent(List<Host> hosts) {
        super(Case.Events.HOSTS_ADDED.name(), hosts);
    }

}
