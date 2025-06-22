package com.tecmfs.common.collections;

/**
 * @param <K> el tipo de las claves
 * @param <V> el tipo de los valores
 */
public class HashMap<K, V> implements Map<K, V> {
    private static final int INITIAL_CAPACITY = 16;
    private Entry<K, V>[] table;
    private int size;

    /**
     * Clase interna que representa un par clave-valor y su enlace a la siguiente entrada (para manejo de colisiones).
     */
    private static class Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;

        Entry(K key, V value, Entry<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    @SuppressWarnings("unchecked")
    public HashMap() {
        table = new Entry[INITIAL_CAPACITY];
        size = 0;
    }

    /**
     * Calcula el índice para la clave usando su hash code y la capacidad de la tabla.
     */
    private int getBucketIndex(K key) {
        int hash = (key == null) ? 0 : key.hashCode();
        return hash & (table.length - 1);
    }

    @Override
    public V put(K key, V value) {
        int index = getBucketIndex(key);
        Entry<K, V> current = table[index];
        // Si ya existe la clave, actualiza el valor.
        while (current != null) {
            if ((key == null && current.key == null) || (key != null && key.equals(current.key))) {
                V oldValue = current.value;
                current.value = value;
                return oldValue;
            }
            current = current.next;
        }
        // Si no existe, añade la nueva entrada al comienzo de la cadena.
        Entry<K, V> newEntry = new Entry<>(key, value, table[index]);
        table[index] = newEntry;
        size++;
        return null;
    }

    @Override
    public V get(Object key) {
        int index = getBucketIndex((K) key);
        Entry<K, V> current = table[index];
        while (current != null) {
            if ((key == null && current.key == null) || (key != null && key.equals(current.key))) {
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    @Override
    public V remove(Object key) {
        int index = getBucketIndex((K) key);
        Entry<K, V> current = table[index];
        Entry<K, V> previous = null;
        while (current != null) {
            if ((key == null && current.key == null) || (key != null && key.equals(current.key))) {
                if (previous == null) {
                    table[index] = current.next;
                } else {
                    previous.next = current.next;
                }
                size--;
                return current.value;
            }
            previous = current;
            current = current.next;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }
        size = 0;
    }
}
