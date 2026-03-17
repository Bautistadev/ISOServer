package com.spring.transactional.iso8583.main.TransactionalPackage.transaction;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  GROUP SELECTOR — Enrutador dinámico de grupos                   ║
 * ║  Equivalente a org.jpos.transaction.GroupSelector de jPOS        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ¿Qué es?
 *   Un TransactionParticipant especial que en su prepare() decide qué
 *   grupo de participantes ejecutar a continuación.
 *
 *   En jPOS, los participantes se organizan en grupos con nombre.
 *   El GroupSelector devuelve el nombre del grupo (o varios separados
 *   por espacios) y el TransactionManager cambia su cadena de ejecución
 *   al grupo indicado.
 *
 * ¿Para qué sirve?
 *   Para enrutar transacciones dinámicamente según el contenido del mensaje.
 *   En lugar de tener un único flujo lineal con ifs internos en cada participante,
 *   cada tipo de transacción tiene su propio grupo de participantes optimizado.
 *
 * Ejemplo real:
 *
 *   Grupos configurados:
 *     "compra"    → [ValidarTarjeta, ValidarSaldo,  Autorizar, ConstruirRespuesta]
 *     "anulacion" → [ValidarReverso, RevertirCargo, ConstruirRespuesta]
 *     "consulta"  → [ConsultarSaldo, ConstruirRespuesta]
 *
 *   GroupSelector implementado como SelectByMTI:
 *     MTI "0200" → return "compra"
 *     MTI "0400" → return "anulacion"
 *     MTI "0100" → return "consulta"
 *
 *   También se pueden encadenar grupos:
 *     return "validacion compra logging"
 *     → ejecuta los tres grupos en secuencia
 *
 * Nota sobre prepare():
 *   El GroupSelector implementa prepare() de TransactionParticipant.
 *   Su prepare() llama internamente a select() y guarda el resultado
 *   en el Context para que el TransactionManager lo lea y cambie el flujo.
 *   Siempre devuelve PREPARED (la selección en sí no puede fallar;
 *   si el MTI no tiene grupo, devuelve un grupo de error).
 */
public interface GroupSelector extends TransactionParticipant {

    /**
     * Decide qué grupo(s) ejecutar para esta transacción.
     *
     * Se llama desde prepare() — el TransactionManager lee el resultado
     * y redirige la ejecución al grupo indicado.
     *
     * @param id      identificador de la sesión
     * @param context contexto de la transacción con el REQUEST ya cargado
     * @return nombre del grupo a ejecutar, o varios separados por espacios.
     *         Null o vacío para continuar con el flujo por defecto sin cambios.
     *
     * Ejemplos de retorno:
     *   "compra"              → ejecutar solo el grupo "compra"
     *   "validacion compra"   → ejecutar "validacion" y luego "compra"
     *   ""                    → no cambiar de grupo, continuar el flujo actual
     */
    String select(long id, Context context);
}