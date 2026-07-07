import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class TestCrypto {
    public static void main(String[] args) throws Exception {
        String encrypted = "T23/7Lz5oB1zKzYQh9G3WvN6QYkG1yU=";
        String key = "your-secure-32-byte-hex-encryption-key-for-tokens";
        
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] iv = new byte[12];
        System.arraycopy(decoded, 0, iv, 0, 12);
        byte[] cipherText = new byte[decoded.length - 12];
        System.arraycopy(decoded, 12, cipherText, 0, cipherText.length);
        
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[32];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 32));
        
        SecretKeySpec keySpec = new SecretKeySpec(finalKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        byte[] decryptedText = cipher.doFinal(cipherText);
        System.out.println("DECRYPTED: " + new String(decryptedText, StandardCharsets.UTF_8));
    }
}
