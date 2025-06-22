package com.tecmfs.common.collections;

import java.util.Iterator;
import java.util.Comparator;

/**
 * Interfaz personalizada que define la funcionalidad básica de una lista.
 *
 * @param <E> el tipo de elemento que contendrá esta lista.
 */
public interface List<E> extends Iterable<E> {

    boolean add(E element);

    void add(int index, E element);

    E set(int index, E element);

    E get(int index);

    E remove(int index);

    boolean remove(E element);

    int size();

    boolean isEmpty();

    void clear();

    /**
     * Ordena la lista usando el comparador dado.
     *
     * @param comparator comparador para definir el orden.
     */
    void sort(java.util.Comparator<? super E> comparator);
}
