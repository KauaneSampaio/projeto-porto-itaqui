package br.jovemtech.itaqui.dominio;

import java.time.LocalDateTime;

/**
 * Registro de auditoria para operações de cenários e lançamentos realizados.
 * Preparado para futura integração com módulo de login/autenticação.
 */
public record RegistroLog(
        String id,
        LocalDateTime dataHora,
        String usuario,
        String operacao,
        String entidade,
        String identificador,
        String detalhes
) {
    public RegistroLog(String id, String usuario, String operacao, String entidade, String identificador, String detalhes) {
        this(id, LocalDateTime.now(), usuario != null && !usuario.isBlank() ? usuario : "gestor.emap", operacao, entidade, identificador, detalhes);
    }
}
