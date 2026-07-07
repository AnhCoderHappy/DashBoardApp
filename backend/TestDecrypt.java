import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class TestDecrypt {
    public static void main(String[] args) throws Exception {
        String encryptedData = "9302c65d962cebeceaf02c98:aae7431f2888b589c7678a440d85a0d1:7396b52a68f7a7e7e828e8ef1f444a2f8a04952eedaff44b7952b86d7c2de087";
        String encryptionKey = "your-secure-32-byte-hex-encryption-key-for-tokens";
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        
        String[] parts = encryptedData.split(":");
        HexFormat hex = HexFormat.of();
        byte[] iv = hex.parseHex(parts[0]);
        byte[] tag = hex.parseHex(parts[1]);
        byte[] cipherText = hex.parseHex(parts[2]);

        byte[] encryptedBytes = new byte[cipherText.length + tag.length];
        System.arraycopy(cipherText, 0, encryptedBytes, 0, cipherText.length);
        System.arraycopy(tag, 0, encryptedBytes, cipherText.length, tag.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        System.out.println("DECRYPTED: " + new String(decryptedBytes, StandardCharsets.UTF_8));
    }
}
