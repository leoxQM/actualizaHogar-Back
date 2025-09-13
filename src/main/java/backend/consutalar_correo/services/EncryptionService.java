package backend.consutalar_correo.services;

public interface EncryptionService {

    String encrypt(String plainText);

    String decrypt(String encryptedText);
}
