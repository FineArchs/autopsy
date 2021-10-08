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
package org.sleuthkit.autopsy.mainui.nodes;


import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactTableDTO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactTableSearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.mainui.nodes.SearchResultChildFactory.ChildKey;
import org.sleuthkit.autopsy.mainui.datamodel.RowResultDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Factory for populating results in a results viewer with a SearchResultsDTO.
 */
public class SearchResultChildFactory extends ChildFactory<ChildKey> {
    private static final Logger logger = Logger.getLogger(SearchResultChildFactory.class.getName());
    private SearchResultsDTO results;

    public SearchResultChildFactory(SearchResultsDTO initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<ChildKey> toPopulate) {
        SearchResultsDTO results = this.results;

        if (results != null) {
            List<ChildKey> childKeys = results.getItems().stream()
                    .map((item) -> new ChildKey(results, item))
                    .collect(Collectors.toList());

            toPopulate.addAll(childKeys);
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(ChildKey key) {
        String typeId = key.getRow().getTypeId();
        try {
            if (DataArtifactTableDTO.getTypeIdForClass().equals(typeId)) {
                return new DataArtifactNode((DataArtifactTableSearchResultsDTO) key.getSearchResults(), (DataArtifactTableDTO) key.getRow());
            } else if (FileRowDTO.getTypeIdForClass().equals(typeId)) {
                return new FileNode(key.getSearchResults(), (FileRowDTO) key.getRow());
            } else {
                logger.log(Level.WARNING, MessageFormat.format("No known node for type id: {0} provided by row result: {1}", typeId, key.getRow()));
            }
        } catch (ClassCastException ex) {
            logger.log(Level.WARNING, MessageFormat.format("Could not cast item with type id: {0} to valid type.", typeId), ex);
        }

        return null;
    }

    public void update(SearchResultsDTO newResults) {
        this.results = newResults;
        this.refresh(false);
    }

    public long getResultCount() {
        return results == null ? 0 : results.getTotalResultsCount();
    }

    static class ChildKey {

        private final SearchResultsDTO searchResults;
        private final RowResultDTO row;

        ChildKey(SearchResultsDTO searchResults, RowResultDTO child) {
            this.searchResults = searchResults;
            this.row = child;
        }

        SearchResultsDTO getSearchResults() {
            return searchResults;
        }

        RowResultDTO getRow() {
            return row;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Objects.hashCode(this.row);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChildKey other = (ChildKey) obj;
            if (!Objects.equals(this.row, other.row)) {
                return false;
            }
            return true;
        }

    }
}
