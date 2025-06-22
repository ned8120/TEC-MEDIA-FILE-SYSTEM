package com.tecmfs.common.collections;

import java.util.Comparator;
import java.util.Iterator;

/**
 * Implementación simple de una lista dinámica similar a java.util.ArrayList.
 *
 * @param <E> el tipo de elementos que se almacenan en la lista.
 */
public class MyArrayList<E> implements List<E>, Iterable<E> {
    private Object[] elements; // Arreglo interno
    private int size;          // Cantidad de elementos
    private static final int INITIAL_CAPACITY = 10;

    public MyArrayList() {
        elements = new Object[INITIAL_CAPACITY];
        size = 0;
    }

    // Nuevo constructor para copiar otra lista
    public MyArrayList(List<E> other) {
        this();
        for (int i = 0; i < other.size(); i++) {
            add(other.get(i));
        }
    }

    @Override
    public boolean add(E element) {
        ensureCapacity(size + 1);
        elements[size++] = element;
        return true;
    }

    @Override
    public void add(int index, E element) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        System.arraycopy(elements, index, elements, index + 1, size - index);
        elements[index] = element;
        size++;
    }

    @Override
    public E set(int index, E element) {
        checkElementIndex(index);
        @SuppressWarnings("unchecked")
        E old = (E) elements[index];
        elements[index] = element;
        return old;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E get(int index) {
        checkElementIndex(index);
        return (E) elements[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public E remove(int index) {
        checkElementIndex(index);
        E removed = (E) elements[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        elements[--size] = null;
        return removed;
    }

    @Override
    public boolean remove(E element) {
        for (int index = 0; index < size; index++) {
            if (elements[index].equals(element)) {
                remove(index);
                return true;
            }
        }
        return false;
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
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > elements.length) {
            int newCapacity = elements.length * 2;
            if (newCapacity < minCapacity) {
                newCapacity = minCapacity;
            }
            Object[] newArray = new Object[newCapacity];
            System.arraycopy(elements, 0, newArray, 0, size);
            elements = newArray;
        }
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Índice: " + index + ", Tamaño: " + size);
        }
    }

    private void checkPositionIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Índice: " + index + ", Tamaño: " + size);
        }
    }

    @Override
    public void sort(Comparator<? super E> comparator) {
        // Implementación con bubble sort (ejemplo simple)
        for (int i = 0; i < size - 1; i++) {
            for (int j = 0; j < size - i - 1; j++) {
                if (comparator.compare(get(j), get(j + 1)) > 0) {
                    Object temp = elements[j];
                    elements[j] = elements[j + 1];
                    elements[j + 1] = temp;
                }
            }
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            private int current = 0;

            @Override
            public boolean hasNext() {
                return current < size;
            }

            @SuppressWarnings("unchecked")
            @Override
            public E next() {
                return (E) elements[current++];
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(elements[i]);
            if (i < size - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
