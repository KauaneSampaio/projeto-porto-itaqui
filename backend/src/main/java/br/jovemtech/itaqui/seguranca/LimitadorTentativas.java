package br.jovemtech.itaqui.seguranca;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Proteção contra força bruta no login com controle duplo:
 * <ul>
 *   <li>Por IP + usuário: bloqueia após 5 falhas em 15 minutos.</li>
 *   <li>Por usuário (global): bloqueia após 15 falhas em 15 minutos (mitiga ataques distribuídos).</li>
 * </ul>
 * Inclui limpeza automática de entradas expiradas para evitar crescimento ilimitado em memória.
 */
@Component
public class LimitadorTentativas {

    private static final int MAX_POR_IP_USUARIO = 5;
    private static final int MAX_GLOBAL_USUARIO = 15;
    private static final int LIMITE_LIMPEZA = 1_000;
    private static final Duration JANELA_BLOQUEIO = Duration.ofMinutes(15);

    private record Estado(int falhas, Instant bloqueadoAte, Instant ultimaTentativa) {
    }

    private final Map<String, Estado> registros = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String ip, String login) {
        return chaveBloqueada("ip:" + ip + "|" + login) || chaveBloqueada("usr:" + login);
    }

    public boolean registrarFalha(String ip, String login) {
        limparExpiradosSeNecessario();
        boolean bloqIp = incrementar("ip:" + ip + "|" + login, MAX_POR_IP_USUARIO);
        boolean bloqUsr = incrementar("usr:" + login, MAX_GLOBAL_USUARIO);
        return bloqIp || bloqUsr;
    }

    public void limpar(String ip, String login) {
        registros.remove("ip:" + ip + "|" + login);
        registros.remove("usr:" + login);
    }

    public void limparTudo() {
        registros.clear();
    }

    private boolean chaveBloqueada(String chave) {
        Estado e = registros.get(chave);
        if (e == null) {
            return false;
        }
        Instant agora = Instant.now();
        if (e.bloqueadoAte() != null) {
            if (agora.isBefore(e.bloqueadoAte())) {
                return true;
            }
            registros.remove(chave);
            return false;
        }
        if (agora.isAfter(e.ultimaTentativa().plus(JANELA_BLOQUEIO))) {
            registros.remove(chave);
        }
        return false;
    }

    private boolean incrementar(String chave, int maximo) {
        Instant agora = Instant.now();
        Estado atualizado = registros.compute(chave, (k, atual) -> {
            if (atual == null || agora.isAfter(atual.ultimaTentativa().plus(JANELA_BLOQUEIO))) {
                return new Estado(1, null, agora);
            }
            int novas = atual.falhas() + 1;
            Instant ate = novas >= maximo ? agora.plus(JANELA_BLOQUEIO) : null;
            return new Estado(novas, ate, agora);
        });
        return atualizado.bloqueadoAte() != null;
    }

    private void limparExpiradosSeNecessario() {
        if (registros.size() < LIMITE_LIMPEZA) {
            return;
        }
        Instant agora = Instant.now();
        registros.entrySet().removeIf(entry -> {
            Estado e = entry.getValue();
            if (e.bloqueadoAte() != null) {
                return agora.isAfter(e.bloqueadoAte());
            }
            return agora.isAfter(e.ultimaTentativa().plus(JANELA_BLOQUEIO));
        });
    }
}
