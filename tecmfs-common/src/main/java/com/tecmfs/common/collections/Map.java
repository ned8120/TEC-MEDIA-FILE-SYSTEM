package com.tecmfs.common.collections;

/**
 * @param <K> el tipo de las claves
 * @param <V> el tipo de los valores
 */
public interface Map<K, V> {

    /**
     * Asocia el valor especificado con la clave dada en este mapa.
     * Si el mapa ya contenía un valor para la clave, se sustituye por el valor dado y se retorna el anterior.
     *
     * @param key   la clave con la que se asociará el valor
     * @param value el valor a asociar
     * @return el valor anterior asociado con la clave o null si no había ninguno
     */
    V put(K key, V value);

    /**
     * Retorna el valor al que está asociada la clave especificada o null si no existe.
     *
     * @param key la clave cuya asociación se desea obtener
     * @return el valor asociado con la clave, o null si no se encuentra
     */
    V get(Object key);

    /**
     * Elimina la asociación para la clave especificada y retorna el valor asociado.
     *
     * @param key la clave cuyo mapeo se debe eliminar
     * @return el valor que se eliminó, o null si no existía
     */
    V remove(Object key);

    /**
     * Retorna true si este mapa contiene una asociación para la clave especificada.
     *
     * @param key la clave cuya presencia se desea verificar
     * @return true si la clave existe en el mapa
     */
    boolean containsKey(Object key);

    /**
     * Retorna el número de asociaciones (clave-valor) en este mapa.
     *
     * @return el tamaño del mapa
     */
    int size();

    /**
     * Indica si el mapa no contiene asociaciones.
     *
     * @return true si el mapa está vacío
     */
    boolean isEmpty();

    /**
     * Elimina todas las asociaciones del mapa.
     */
    void clear();
}