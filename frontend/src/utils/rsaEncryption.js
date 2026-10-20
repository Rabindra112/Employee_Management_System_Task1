import { api } from "../api/client";

export async function encryptPassword(password) {

    // Fetch RSA public key from Spring Boot
    const response = await api.get("/auth/public-key");

    const publicKey = response.data.publicKey;

    // Convert Base64 public key to bytes
    const binary = atob(publicKey);

    const keyBytes = Uint8Array.from(
        binary,
        char => char.charCodeAt(0)
    );

    // Import RSA public key
    const cryptoKey = await window.crypto.subtle.importKey(
        "spki",
        keyBytes.buffer,
        {
            name: "RSA-OAEP",
            hash: "SHA-256"
        },
        false,
        ["encrypt"]
    );

    // Encrypt password
    const encrypted = await window.crypto.subtle.encrypt(
        {
            name: "RSA-OAEP"
        },
        cryptoKey,
        new TextEncoder().encode(password)
    );

    // Convert encrypted bytes to Base64
    const encryptedBytes = new Uint8Array(encrypted);

    let binaryString = "";

    encryptedBytes.forEach(byte => {
        binaryString += String.fromCharCode(byte);
    });

    return btoa(binaryString);
}