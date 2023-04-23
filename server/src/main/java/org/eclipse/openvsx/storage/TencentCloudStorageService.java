package org.eclipse.openvsx.storage;

import com.google.common.base.Strings;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.CopyObjectRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TempFile;
import org.eclipse.openvsx.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;

// 腾讯云 Cos 文档: https://cloud.tencent.com/document/product/436/57316
@Component
public class TencentCloudStorageService implements IStorageService {

    private static final Logger logger = LoggerFactory.getLogger(TencentCloudStorageService.class);

    @Value("${ovsx.storage.qcloud.cos.secret-id:}")
    String secretId;

    @Value("${ovsx.storage.qcloud.cos.secret-key:}")
    String secretKey;

    @Value("${ovsx.storage.qcloud.cos.region:}")
    String region;

    @Value("${ovsx.storage.qcloud.cos.bucket-name:}")
    String bucketName;

    @Value("${ovsx.storage.qcloud.cos.openvsx-dir:}")
    String openvsxDir;

    @Value("${ovsx.storage.qcloud.cos.endpoint}")
    private String endpoint;

    String getEndpoint() {
        if (endpoint != null && endpoint.endsWith("/")) {
            return endpoint;
        }
        endpoint = String.format("https://%s.cos.%s.myqcloud.com/", bucketName, region);
        logger.info("Use default Tencent COS endpoint: {}", endpoint);
        return endpoint;
    }

    private COSClient cosClient;

    private TransferManager transferManager;

    @Override
    public boolean isEnabled() {
        return !Strings.isNullOrEmpty(bucketName);
    }

    protected COSClient getCosClient() {
        if (cosClient == null) {
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            cosClient = new COSClient(cred, clientConfig);
        }
        return cosClient;
    }

    // 创建 TransferManager 实例，这个实例用来后续调用高级接口
    TransferManager createTransferManager() {
        if (transferManager == null) {
            // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
            // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
            var threadPool = Executors.newFixedThreadPool(16);
            // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
            transferManager = new TransferManager(getCosClient(), threadPool);
            // 设置高级接口的配置项
            // 分块上传阈值和分块大小分别为 5MB 和 1MB
            TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
            transferManagerConfiguration.setMultipartUploadThreshold(5*1024*1024);
            transferManagerConfiguration.setMinimumUploadPartSize(1*1024*1024);
            transferManager.setConfiguration(transferManagerConfiguration);
        }
        return transferManager;
    }

    @Override
    public void uploadFile(FileResource resource) {
        var key = getKey(getCosName(resource));
        uploadFile(resource.getContent(), resource.getName(), key);
        logger.info("UploadFile to qcloud COS, key: {}", key);
    }

    @Override
    public void uploadFile(FileResource resource, TempFile file) {
        var key = getKey(getCosName(resource));
        uploadFile(file.getPath().toAbsolutePath().toFile(), resource.getName(), key);
        logger.info("UploadFile to qcloud COS, key: {}", key);
    }

    @Override
    public void removeFile(FileResource resource) {
        var key = getKey(getCosName(resource));
        removeFile(key);
        logger.info("Remove file from qcloud COS, key: {}", key);
    }

    @Override
    public URI getLocation(FileResource resource) {
        var key = getKey(getCosName(resource));
        // 类似 https://yourcosname.cos.ap-guangzhou.myqcloud.com/
        var url = getEndpoint() + key;
        logger.info("Get location, url: {}", url);
        return URI.create(url);
    }

    @Override
    public void uploadNamespaceLogo(Namespace namespace) {
        var key = getKey(getCosName(namespace));
        uploadFile(namespace.getLogoBytes(), namespace.getName() , key);
        logger.info("UploadNamespaceLogo to qcloud COS, key: {}", key);
    }

    @Override
    public void removeNamespaceLogo(Namespace namespace) {
        var key = getKey(getCosName(namespace));
        removeFile(key);
        logger.info("Remove nameSpace logo file from qcloud COS, key: {}", key);
    }

    @Override
    public URI getNamespaceLogoLocation(Namespace namespace) {
        var key = getKey(getCosName(namespace));
        // 类似 https://yourcosname.cos.ap-guangzhou.myqcloud.com/
        String url = getEndpoint() + key;
        logger.info("Get namespace logo location, url: {}", url);
        return URI.create(url);
    }

    @Override
    public TempFile downloadNamespaceLogo(Namespace namespace) {
        var key = getKey(getCosName(namespace));
        var getObjectRequest = new GetObjectRequest(bucketName, key);
        try {
            var logoFile = new TempFile("namespace-logo", ".png");
            createTransferManager().download(getObjectRequest, logoFile.getPath().toAbsolutePath().toFile(), true);
            logger.info("DownloadNamespaceLogo from qcloud COS, key: {}", key);
            return logoFile;
        } catch (CosServiceException e) {
            throw new CosServiceException("DownloadNamespaceLogo to qcloud Cos, file: " + key +
                    ", CosServiceException: " + e.getMessage());
        } catch (CosClientException e) {
            throw new CosServiceException("DownloadNamespaceLogo to qcloud Cos, file: " + key +
                    ", CosClientException: " + e.getMessage());
        } catch (IOException e) {
            throw new CosServiceException("DownloadNamespaceLogo to qcloud Cos, file: " + key +
                    ", IOException: " + e.getMessage());
        }
    }

    @Override
    public void copyFiles(List<Pair<FileResource, FileResource>> pairs) {
        for (Pair<FileResource, FileResource> pair : pairs) {
            FileResource source = pair.getFirst();
            FileResource target = pair.getSecond();
            var sourceKey = getKey(source.getName());
            var destinationKey = getKey(target.getName());
            CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, sourceKey, bucketName, destinationKey);
            createTransferManager().copy(copyObjectRequest, null);
            logger.info("CopyFiles in qcloud COS, sourceKey: {}, sourceKey: {}", sourceKey, destinationKey);
        }
    }

    protected void uploadFile(byte[] content, String fileName, String key) {
        logger.info("UploadFile to qcloud COS, key: {}", key);
        var metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(StorageUtil.getFileType(fileName).toString());
        if (fileName.endsWith(".vsix")) {
            metadata.setContentDisposition("attachment; filename=\"" + fileName + "\"");
        } else {
            var cacheControl = StorageUtil.getCacheControl(fileName);
            metadata.setCacheControl(cacheControl.getHeaderValue());
        }
        var inputStream = new ByteArrayInputStream(content);
        var request = new PutObjectRequest(bucketName, key, inputStream, metadata);
        try {
            // 高级接口会返回一个异步结果Upload
            createTransferManager().upload(request);
        } catch (CosServiceException e) {
            throw new CosServiceException("UploadFile to qcloud Cos, file: " + key +
                    ", CosServiceException: " + e.getMessage());
        } catch (CosClientException e) {
            throw new CosServiceException("UploadFile to qcloud Cos, file: " + key +
                    ", CosClientException: " + e.getMessage());
        }
    }

    protected void uploadFile(File file, String fileName, String key) {
        logger.info("UploadFile to qcloud COS, key: {}", key);
        var metadata = new ObjectMetadata();
        metadata.setContentLength(file.length());
        metadata.setContentType(StorageUtil.getFileType(fileName).toString());
        if (fileName.endsWith(".vsix")) {
            metadata.setContentDisposition("attachment; filename=\"" + fileName + "\"");
        } else {
            var cacheControl = StorageUtil.getCacheControl(fileName);
            metadata.setCacheControl(cacheControl.getHeaderValue());
        }
        var request = new PutObjectRequest(bucketName, key, file)
                .withMetadata(metadata);
        try {
            // 高级接口会返回一个异步结果Upload
            createTransferManager().upload(request);
        } catch (CosServiceException e) {
            throw new CosServiceException("UploadFile to qcloud Cos, file: " + key +
                    ", CosServiceException: " + e.getMessage());
        } catch (CosClientException e) {
            throw new CosServiceException("UploadFile to qcloud Cos, file: " + key +
                    ", CosClientException: " + e.getMessage());
        }
    }

    protected void removeFile(String key) {
        try {
            cosClient.deleteObject(bucketName, key);
        } catch (CosServiceException e) {
            throw new CosServiceException("Remove qcloud Cos, file: " + key +
                    ", CosServiceException: " + e.getMessage());
        } catch (CosClientException e) {
            throw new CosServiceException("Remove qcloud Cos, file: " + key +
                    ", CosClientException: " + e.getMessage());
        }
    }

    protected String getKey(String key) {
        return Paths.get(openvsxDir, key).toString();
    }

    protected String getCosName(FileResource resource) {
        var extVersion = resource.getExtension();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var segments = new String[]{namespace.getName(), extension.getName()};
        if(!TargetPlatform.isUniversal(extVersion)) {
            segments = ArrayUtils.add(segments, extVersion.getTargetPlatform());
        }

        segments = ArrayUtils.add(segments, extVersion.getVersion());
        segments = ArrayUtils.addAll(segments, resource.getName().split("/"));
        return UrlUtil.createApiUrl("", segments).substring(1); // remove first '/'
    }

    protected String getCosName(Namespace namespace) {
        return UrlUtil.createApiUrl("", namespace.getName(), "logo", namespace.getLogoName()).substring(1); // remove first '/'
    }
}