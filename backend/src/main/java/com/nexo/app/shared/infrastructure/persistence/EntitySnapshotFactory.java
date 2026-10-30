package com.nexo.app.shared.infrastructure.persistence;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.AuditIgnore;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gera o snapshot plano (profundidade 1) de uma entidade para a trilha de auditoria JSONB.
 *
 * <p>Serializar a entidade com Jackson percorria o grafo inteiro de relacionamentos, o que
 * provocava recursão infinita em relações bidirecionais (ex.: {@code Desconto <-> DescontoItem})
 * e forçava a inicialização de proxies LAZY a cada leitura.</p>
 *
 * <p>Aqui nenhuma associação é percorrida:</p>
 * <ul>
 *   <li>Relações {@code *ToOne} viram apenas {@code <campo>Id} — o identificador é lido do
 *       proxy sem disparar SELECT adicional.</li>
 *   <li>Coleções {@code *ToMany} são omitidas: cada filho estende {@link BaseEntity} e portanto
 *       já produz o próprio registro em {@code logs_sistema}.</li>
 *   <li>Qualquer valor ainda não inicializado pelo Hibernate é ignorado, nunca carregado.</li>
 * </ul>
 *
 * @author <b>Thalisson<b/>
 * @version <b>1.0<b/>
 */
public final class EntitySnapshotFactory {

    private static final Map<Class<?>, List<Field>> CACHE_CAMPOS = new ConcurrentHashMap<>();

    private EntitySnapshotFactory() {
    }

    /**
     * Monta o mapa de auditoria da entidade, sem percorrer relacionamentos.
     */
    public static Map<String, Object> criar(Object entidade) {
        if (entidade == null) return null;

        Object alvo = Hibernate.unproxy(entidade);
        Map<String, Object> snapshot = new LinkedHashMap<>();

        for (Field campo : camposAuditaveis(alvo.getClass())) {
            try {
                Object valor = campo.get(alvo);
                if (ehAssociacaoSimples(campo)) {
                    snapshot.put(campo.getName() + "Id", identificadorDe(valor));
                } else {
                    snapshot.put(campo.getName(), normalizar(valor));
                }
            } catch (IllegalAccessException e) {
                // Um campo inacessível não pode invalidar o restante do snapshot.
            }
        }
        return snapshot;
    }

    private static List<Field> camposAuditaveis(Class<?> tipo) {
        return CACHE_CAMPOS.computeIfAbsent(tipo, EntitySnapshotFactory::mapearCampos);
    }

    private static List<Field> mapearCampos(Class<?> tipo) {
        List<Field> campos = new ArrayList<>();
        for (Class<?> atual = tipo; atual != null && atual != Object.class; atual = atual.getSuperclass()) {
            for (Field campo : atual.getDeclaredFields()) {
                if (deveIgnorar(campo)) continue;
                campo.setAccessible(true);
                campos.add(campo);
            }
        }
        return List.copyOf(campos);
    }

    private static boolean deveIgnorar(Field campo) {
        return campo.isSynthetic()
                || Modifier.isStatic(campo.getModifiers())
                || Modifier.isTransient(campo.getModifiers())
                || campo.isAnnotationPresent(AuditIgnore.class)
                || campo.isAnnotationPresent(Transient.class)
                || ehColecaoDeEntidades(campo);
    }

    /**
     * Coleções de entidades (*ToMany) ficam de fora do snapshot: percorrê-las é justamente
     * o que fecha o ciclo da recursão e o que dispara o carregamento LAZY em cascata.
     */
    private static boolean ehColecaoDeEntidades(Field campo) {
        return campo.isAnnotationPresent(OneToMany.class) || campo.isAnnotationPresent(ManyToMany.class);
    }

    private static boolean ehAssociacaoSimples(Field campo) {
        return campo.isAnnotationPresent(ManyToOne.class) || campo.isAnnotationPresent(OneToOne.class);
    }

    /**
     * Extrai o id da associação. Em proxies LAZY o identificador já está disponível no
     * inicializador, então a leitura não força nenhum acesso ao banco.
     */
    private static Object identificadorDe(Object associacao) {
        if (associacao == null) return null;
        if (associacao instanceof HibernateProxy proxy) {
            Object id = proxy.getHibernateLazyInitializer().getIdentifier();
            return id != null ? id.toString() : null;
        }
        if (associacao instanceof BaseEntity entidade) {
            return entidade.getId() != null ? entidade.getId().toString() : null;
        }
        return null;
    }

    /**
     * Converte o valor para um tipo que o PostgreSQL aceita em JSONB, mantendo datas e
     * identificadores como texto ISO-8601 / UUID.
     */
    private static Object normalizar(Object valor) {
        if (valor == null) return null;
        if (!Hibernate.isInitialized(valor)) return null;
        if (valor instanceof String || valor instanceof Number || valor instanceof Boolean) return valor;
        if (valor instanceof byte[] bytes) return "[binário: " + bytes.length + " bytes]";
        if (valor instanceof BaseEntity entidade) return identificadorDe(entidade);
        if (valor instanceof Collection<?> colecao) {
            List<Object> itens = new ArrayList<>(colecao.size());
            colecao.forEach(item -> itens.add(normalizar(item)));
            return itens;
        }
        if (valor instanceof Map<?, ?> mapa) {
            Map<String, Object> copia = new LinkedHashMap<>();
            mapa.forEach((chave, item) -> copia.put(String.valueOf(chave), normalizar(item)));
            return copia;
        }
        return valor.toString();
    }
}
