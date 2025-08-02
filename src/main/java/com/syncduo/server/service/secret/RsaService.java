package com.syncduo.server.service.secret;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Decoder;

@Slf4j
@Service
public class RsaService {

    @Value("${syncduo.server.rsa.private.key.path}")
    private Resource privateKeyResource;

    private PrivateKey privateKey;

    private static final Decoder BASE64_DECODER = Base64.getDecoder();

    public void init() throws SyncDuoException {
        try {
            String pemContent = Files.readString(privateKeyResource.getFile().toPath());
            String cleaned = pemContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = BASE64_DECODER.decode(cleaned);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.privateKey = keyFactory.generatePrivate(keySpec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new SyncDuoException("init failed. ", e);
        }
    }

    public String decrypt(String base64Encrypted) throws SyncDuoException {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(BASE64_DECODER.decode(base64Encrypted));
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new SyncDuoException("decrypt failed", e);
        }
    }
}
