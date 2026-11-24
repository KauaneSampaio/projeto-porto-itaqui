package br.jovemtech.itaqui.seguranca;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hash seguro de senhas com PBKDF2-HMAC-SHA256 (210.000 iterações, salt aleatório de 16 bytes
 * e comparação em tempo constante para evitar timing attacks).
 */
public final class HashSenha {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACOES = 210_000;
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_CHAVE_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Hash dummy gerado uma vez para manter tempo constante mesmo quando o usuário não existe
    private static final String HASH_FICTICIO = gerar("senha-inexistente-constante-itaqui");

    private HashSenha() {
    }

    public static String gerar(String senha) {
        byte[] salt = new byte[TAMANHO_SALT];
        RANDOM.nextBytes(salt);
        byte[] hash = derivar(senha.toCharArray(), salt, ITERACOES);
        return "pbkdf2_sha256$" + ITERACOES + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(String senha, String armazenado) {
        if (senha == null || armazenado == null) {
            return false;
        }
        String[] partes = armazenado.split("\\$");
        if (partes.length != 4 || !"pbkdf2_sha256".equals(partes[0])) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            byte[] calculado = derivar(senha.toCharArray(), salt, iteracoes);
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Executa uma verificação fictícia para equalizar o tempo de resposta quando o login não existe. */
    public static void verificarFicticio(String senha) {
        verificar(senha == null ? "" : senha, HASH_FICTICIO);
    }

    public static String gerarTokenSeguro() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] derivar(char[] senha, byte[] salt, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha, salt, iteracoes, TAMANHO_CHAVE_BITS);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITMO);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Falha ao derivar hash PBKDF2", e);
        } finally {
            spec.clearPassword();
        }
    }
}
