# Decisiones de diseno

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

## Capa de presentacion

La interfaz grafica esta en `app.clj` y usa **Swing**, que viene incluido en el JDK. Se eligio Swing en lugar de JavaFX para evitar dependencias externas con binarios nativos por plataforma: asi `lein run` funciona sin configuracion adicional en cualquier sistema operativo, que es justamente la condicion de correccion (clonar en limpio y ejecutar).

`app.clj` concentra toda la impureza del programa:

- abrir y guardar archivos (`ImageIO`),
- dibujar la imagen y manejar eventos de los botones,
- el estado mutable de la aplicacion.

El namespace `filters.clj` no conoce a `app.clj`: la dependencia va siempre `core` → `app` → `filters`.

## Estado de la aplicacion

El estado vive en un unico `atom`:

```clojure
{:imagen-original ...  ; la imagen tal como se cargo
 :imagen-actual   ...  ; la imagen que se esta mostrando
 :archivo         ...  ; el archivo asociado (para "Guardar")
 :pipeline        []}  ; vector de ids de filtros
```

Mantener la imagen original aparte permite que "Aplicar" siempre parta de ella y que "Reset" la restaure sin reabrir el archivo.

## Concurrencia

Aplicar el pipeline puede tardar, asi que no debe correr en el hilo de la interfaz (si no, la ventana se congela). El esquema es:

1. Al presionar **Aplicar**, se marca la UI como "Procesando…" (botones deshabilitados y cursor de reloj).
2. El calculo se lanza en otro hilo con `future`.
3. Cuando termina, el refresco de la imagen vuelve al hilo de la interfaz con `SwingUtilities/invokeLater` (Swing solo permite tocar la UI desde su propio hilo).

Ademas, cada filtro paraleliza su trabajo internamente con `pmap` (una fila por tarea), aprovechando todos los cores. Es seguro porque cada pixel de salida se calcula leyendo la imagen original, que es inmutable durante ese filtro.
