import sys
import urllib.request
import json
from Crypto.Cipher import AES

def decrypt(encrypted_text, key_hex):
    # key is a 32-byte string (not hex, wait, the ENCRYPTION_KEY in .env is "your-secure-32-byte-hex-encryption-key-for-tokens" which is a string.
    # Actually, TokenService does: MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
    import hashlib
    key_bytes = hashlib.sha256(key_hex.encode('utf-8')).digest()
    
    parts = encrypted_text.split(':')
    iv = bytes.fromhex(parts[0])
    tag = bytes.fromhex(parts[1])
    ciphertext = bytes.fromhex(parts[2])
    
    cipher = AES.new(key_bytes, AES.MODE_GCM, nonce=iv)
    decrypted = cipher.decrypt_and_verify(ciphertext, tag)
    return decrypted.decode('utf-8')

if __name__ == '__main__':
    key = "your-secure-32-byte-hex-encryption-key-for-tokens"
    enc = "9302c65d962cebeceaf02c98:aae7431f2888b589c7678a440d85a0d1:7396b52a68f7a7e7e828e8ef1f444a2f8a04952eedaff44b7952b86d7c2de087"
    try:
        api_key = decrypt(enc, key)
        print("Decrypted API Key:", api_key)
        
        # Now fetch from pancake
        url = f"https://pos.pages.fm/api/v1/shops/230325265/orders?api_key={api_key}"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            print("Total entries:", data.get('total_entries'))
            print("Data length:", len(data.get('data', [])))
            if data.get('data'):
                print("First order:", json.dumps(data['data'][0])[:500])
    except Exception as e:
        print("Error:", e)
