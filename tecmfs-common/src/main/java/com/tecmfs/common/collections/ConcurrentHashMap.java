package com.tecmfs.common.collections;

/**
 * @param <K> Tipo de las claves.
 * @param <V> Tipo de los valores.
 */
public class ConcurrentHashMap<K, V> implements Map<K, V> {
    // Usaremos nuestra implementación previa de HashMap para almacenar los datos.
    private final HashMap<K, V> map;
    private final Object lock = new Object();

    public ConcurrentHashMap() {
        map = new HashMap<>();
    }

    @Override
    public V put(K key, V value) {
        // En ConcurrentHashMap no se permiten claves o valores nulos.
        if (key == null || value == null) {
            throw new NullPointerException("Claves y valores nulos no están permitidos");
        }
        synchronized(lock) {
            return map.put(key, value);
        }
    }

    @Override
    public V get(Object key) {
        if (key == null) {
            throw new NullPointerException("Claves nulas no están permitidas");
        }
        synchronized(lock) {
            return map.get(key);
        }
    }

    @Override
    public V remove(Object key) {
        if (key == null) {
            throw new NullPointerException("Claves nulas no están permitidas");
        }
        synchronized(lock) {
            return map.remove(key);
        }
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            throw new NullPointerException("Claves nulas no están permitidas");
        }
        synchronized(lock) {
            return map.containsKey(key);
        }
    }

    @Override
    public int size() {
        synchronized(lock) {
            return map.size();
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized(lock) {
            return map.isEmpty();
        }
    }

    @Override
    public void clear() {
        synchronized(lock) {
            map.clear();
        }
    }
}
