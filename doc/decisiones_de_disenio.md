# Decisiones de diseno - nucleo funcional

## Objetivo

La parte funcional implementa los filtros de procesamiento de imagenes y la aplicacion de un pipeline de filtros. No depende de la interfaz grafica ni de la entrada/salida de archivos.

## Modelo de imagen

Los filtros trabajan con `BufferedImage`, que representa una imagen como una matriz de pixeles.

Cada pixel se procesa a partir de sus canales ARGB:

- `A`: alfa o transparencia.
- `R`: rojo.
- `G`: verde.
- `B`: azul.

Internamente se usan funciones auxiliares para convertir entre el entero ARGB usado por `BufferedImage` y un mapa de canales:

```clojure
{:a 25 :r 122 :g 250 :b 40}
```

## Funciones auxiliares

- `limitar-canal`: asegura que un valor quede entre `0` y `255`.
- `argb->canales`: separa un entero ARGB en sus canales.
- `canales->argb`: vuelve a empaquetar los canales en un entero ARGB.
- `mapear-pixeles`: aplica una funcion a cada pixel y devuelve una imagen nueva.

Estas funciones permiten implementar filtros puntuales sin depender de otras capas del programa.

## Filtros implementados

- `desaturar`: convierte cada pixel a escala de grises promediando rojo, verde y azul.
- `invertir`: invierte cada canal usando `255 - valor`.
- `brillo`: suma un valor fijo a cada canal RGB y recorta el resultado al rango valido.
- `difuminar`: aplica una convolucion de caja de `3x3`, promediando cada pixel con sus vecinos.

## Convolucion

La convolucion usa un kernel, que es una matriz de pesos. Para `difuminar` se usa:

```clojure
[[1 1 1]
 [1 1 1]
 [1 1 1]]
```

Cada pixel nuevo se calcula mirando el pixel original y sus vecinos. Como el kernel tiene nueve valores, el resultado se divide por `9`.

En los bordes de la imagen, si una coordenada queda fuera del rango valido, se usa la coordenada valida mas cercana.

## Pipeline

El pipeline se representa como un vector de ids de filtros:

```clojure
[:desaturar :difuminar :invertir]
```

La funcion `aplicar-pipeline` aplica los filtros en orden con `reduce`.

## Independencia

El nucleo funcional solo recibe una imagen, aplica transformaciones y devuelve una imagen nueva.

Esto permite testearlo por separado y reutilizarlo desde una interfaz grafica, una interfaz de consola u otra capa de entrada/salida.
