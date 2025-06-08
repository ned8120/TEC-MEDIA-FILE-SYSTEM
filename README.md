# PROYECTO 3 TEC MEDIA FILE SYSTEM

**Instituto Tecnológico de Costa Rica**  
**Escuela de Ingeniería en Computadores**  
**Algoritmos y Estructuras de Datos II (CE 2103)**  
**I Semestre 2025**

---

## Objetivos

### General
- Diseñar e implementar un Sistema de Archivos Distribuido.

### Específicos
- Investigar e implementar niveles de RAID.
- Investigar y desarrollar una aplicación en el lenguaje de programación de su preferencia.
- Aprender e implementar conceptos de computación distribuida.
- Aplicar conocimientos adquiridos de organización de discos, registros, file systems, RAID levels y archivos.
- Aplicar patrones de diseño en la solución de un problema.
- Diseñar e implementar un protocolo de comunicación en formato JSON.

---

## Requerimientos

**TEC Media File System (TECMFS)** es un sistema de archivos bajo un esquema **RAID 5**, construido mediante una arquitectura distribuida llamada “Shared Disk Architecture”.

### Descripción de Componentes

#### 1. Disk Node (TECMFS-Disk) - 30 puntos

- Los Disk Node almacenan los datos del cliente (bytes) o paridad.
- Cada nodo ejecuta la aplicación TECMFS-Disk, configurada por un archivo XML con:
  - IP / Puerto (para comunicación con el Controller Node vía GRPC/HTTP)
  - Path: ubicación en el sistema de archivos donde se guardan los datos.
- El Disk Node debe tener tamaño fijo y bloques de igual tamaño (definido por el grupo).
- Todos los nodos deben tener el mismo tamaño de almacenamiento y bloque.
- Requiere al menos 4 nodos funcionales para RAID 5. Cada bloque contiene datos o paridad.

#### 2. Controller Node (Servidor en C++) - 30 puntos

- Gestiona almacenamiento y lectura en los nodos.
- Gestiona distribución de bloques y cálculo de paridad para tolerancia a fallos.
- Estructura para identificar ubicación de bloques dentro del RAID.
- Permite recuperación de documentos aun con la pérdida de un disco.
- Al iniciar, se debe especificar el puerto de comunicación.

#### 3. Interfaz GRPC/HTTP del Controller Node - 10 puntos

- Agregar/Eliminar documentos de cualquier tipo.
- Buscar documento por nombre.
- Descargar documento (unir bloques y tolerar falla de un disco).

#### 4. Interfaz de Estado - 10 puntos

- Provee una interfaz sencilla mostrando el estado de cada bloque de cada nodo del RAID.

#### 5. Aplicación con Interfaz Gráfica - 20 puntos

- Permite listar, buscar, agregar o eliminar documentos PDF.
- Se comunica con el Controller Node por GRPC/HTTP.

---

> Cualquier aspecto no especificado queda a criterio del grupo para cumplir los requerimientos.

---

## Aspectos Operativos

- Trabajo en grupos de cuatro personas.
- Uso obligatorio de Git y GitHub.
- Fecha de entrega según TEC Digital (se sube un PDF con la documentación).
- El código puede seguir desarrollándose hasta 15 minutos antes de la revisión oficial.

---

## Documentación

Debe contener:

- Portada
- Introducción
- Tabla de contenidos (títulos numerados)
- Breve descripción del problema
- Descripción de la solución:
  - Explicación de implementación por requerimiento, alternativas, limitaciones, problemas encontrados, etc.
- Diseño general: Diagrama de clases UML y patrones de diseño aplicados.
- Enlace al repositorio de GitHub.

---

## Evaluación

- Valor total: 15% de la nota del curso.
- Requisitos indispensables para revisión:
  - Solución completamente integrada.
  - Interfaz de usuario implementada e integrada.
- Calificación:
  - Código: 80%
  - Documentación: 10%
  - Defensa: 10%
- Restricciones:
  - No entregar documentación en PDF: nota 0.
  - No usar manejador de código: nota 0.
  - No entregar documentación en fecha: nota 0.
  - Nota de documentación proporcional a completitud del código.
- La revisión de la documentación es realizada por el profesor.
- Defensa: 20 min. por grupo; deben mostrar todo el trabajo realizado.
- Cada grupo debe llevar el equipo necesario para la revisión.
- Solo miembros del grupo, asistentes, profesores y coordinador pueden estar en la revisión.
- Todas las estructuras de datos requeridas deben ser implementadas desde cero por los estudiantes.

---
