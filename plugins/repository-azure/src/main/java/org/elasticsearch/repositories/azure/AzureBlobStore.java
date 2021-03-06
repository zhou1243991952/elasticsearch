/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.repositories.azure;

import com.microsoft.azure.storage.LocationMode;
import com.microsoft.azure.storage.StorageException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.metadata.RepositoryMetaData;
import org.elasticsearch.common.blobstore.BlobContainer;
import org.elasticsearch.common.blobstore.BlobMetaData;
import org.elasticsearch.common.blobstore.BlobPath;
import org.elasticsearch.common.blobstore.BlobStore;
import org.elasticsearch.repositories.azure.AzureRepository.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Map;

import static java.util.Collections.emptyMap;

public class AzureBlobStore implements BlobStore {
    
    private static final Logger logger = LogManager.getLogger(AzureBlobStore.class);

    private final AzureStorageService service;

    private final String clientName;
    private final String container;
    private final LocationMode locationMode;

    public AzureBlobStore(RepositoryMetaData metadata, AzureStorageService service)
            throws URISyntaxException, StorageException {
        this.container = Repository.CONTAINER_SETTING.get(metadata.settings());
        this.clientName = Repository.CLIENT_NAME.get(metadata.settings());
        this.service = service;
        // locationMode is set per repository, not per client
        this.locationMode = Repository.LOCATION_MODE_SETTING.get(metadata.settings());
        final Map<String, AzureStorageSettings> prevSettings = this.service.refreshAndClearCache(emptyMap());
        final Map<String, AzureStorageSettings> newSettings = AzureStorageSettings.overrideLocationMode(prevSettings, this.locationMode);
        this.service.refreshAndClearCache(newSettings);
    }

    @Override
    public String toString() {
        return container;
    }

    /**
     * Gets the configured {@link LocationMode} for the Azure storage requests.
     */
    public LocationMode getLocationMode() {
        return locationMode;
    }

    public String getClientName() {
        return clientName;
    }

    @Override
    public BlobContainer blobContainer(BlobPath path) {
        return new AzureBlobContainer(path, this);
    }

    @Override
    public void delete(BlobPath path) throws IOException {
        final String keyPath = path.buildAsString();
        try {
            service.deleteFiles(clientName, container, keyPath);
        } catch (URISyntaxException | StorageException e) {
            logger.warn("cannot access [{}] in container {{}}: {}", keyPath, container, e.getMessage());
            throw new IOException(e);
        }
    }

    @Override
    public void close() {
    }

    public boolean containerExist() throws URISyntaxException, StorageException {
        return service.doesContainerExist(clientName, container);
    }

    public boolean blobExists(String blob) throws URISyntaxException, StorageException {
        return service.blobExists(clientName, container, blob);
    }

    public void deleteBlob(String blob) throws URISyntaxException, StorageException {
        service.deleteBlob(clientName, container, blob);
    }

    public InputStream getInputStream(String blob) throws URISyntaxException, StorageException, IOException {
        return service.getInputStream(clientName, container, blob);
    }

    public Map<String, BlobMetaData> listBlobsByPrefix(String keyPath, String prefix)
        throws URISyntaxException, StorageException {
        return service.listBlobsByPrefix(clientName, container, keyPath, prefix);
    }

    public void writeBlob(String blobName, InputStream inputStream, long blobSize, boolean failIfAlreadyExists)
        throws URISyntaxException, StorageException, FileAlreadyExistsException {
        service.writeBlob(this.clientName, container, blobName, inputStream, blobSize, failIfAlreadyExists);
    }
}
