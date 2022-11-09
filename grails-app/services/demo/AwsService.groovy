package demo

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.Protocol
import com.amazonaws.SdkClientException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.*
import com.convertlab.rest.RestUtils
import grails.converters.JSON
import org.springframework.beans.factory.annotation.Value

class AwsService {

    @Value('${aws.s3.region}')
    String region

    @Value('${aws.s3.accessKey}')
    String accessKey

    @Value('${aws.s3.secretKey}')
    String secretKey

    @Value('${aws.s3.bucketName}')
    String bucketName

    AmazonS3 client = null

    def upload(Map fileInfoData) {
        log.info("New AWS S3 upload begin: ${fileInfoData.ossKey}," +
                " bucket: $bucketName, region: $region")

        client = getClient()

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName, fileInfoData.ossKey.toString(), fileInfoData.file).withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams())
            client.putObject(putObjectRequest)
            return true
        } catch (Exception ex) {
            log.error("====service exception, errorMessage ${ex.message}")
        }
        return false
    }

    def listFile(){
        client = getClient()

        try {
            def res  =  client.listObjects(bucketName)
            res.each {
                System.out.println(it as JSON)
            }
            return true
        } catch (Exception ex) {
            log.error("====service exception, errorMessage ${ex.message}")
        }
        return false
    }

    private static long calKb(Long val) {
        return val/1024;
    }

    def download(String cosKey) {
        log.info("download begin: $cosKey," +
                " bucket: $bucketName, region: $region")

        client = getClient()

        try {
            S3Object fileObject = client.getObject(bucketName, cosKey)
            return fileObject.getObjectContent().bytes
        } catch (AmazonServiceException ae) {
            log.warn("====service exception, errorCode ${ae.errorCode} errorMessage ${ae.message}")
        } catch (SdkClientException se) {
            log.error("====service exception, errorMessage ${se.message}")
        }
        return null
    }

    AmazonS3 getClient() {
        if (!client) {
            try {
                ClientConfiguration clientConfiguration = new ClientConfiguration()
                AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey)
                client = AmazonS3ClientBuilder.standard()
                        .withRegion(Regions.fromName(region))
                        .withCredentials(new AWSStaticCredentialsProvider(credentials))
                        .withClientConfiguration(getAwsProxyConfig(clientConfiguration))
                        .build()

                boolean exists = client.doesBucketExistV2(bucketName)
                if (!exists) {
                    client.createBucket(new CreateBucketRequest(bucketName))
                }
            } catch (Exception e) {
                log.error("====service exception, errorMessage ${e.message}")
            }
        }
        client
    }

    def getAwsProxyConfig(ClientConfiguration clientConfig) {
        String proxyAddr = RestUtils.getHttpProxy()
        if (proxyAddr == null || proxyAddr.isEmpty()) {
            log.info("HttpProxy disabled")
        } else {
            def proxyUri = new URI(proxyAddr)
            clientConfig.setProxyHost(proxyUri.getHost())
            if (Protocol.HTTP.toString() == proxyUri.getScheme()) {
                clientConfig.setProtocol(Protocol.HTTP)
            } else {
                clientConfig.setProtocol(Protocol.HTTPS)
            }
            def proxyPort = proxyUri.getPort()
            if (proxyPort) {
                clientConfig.setProxyPort(proxyPort)
            }
        }
        return clientConfig
    }
}
