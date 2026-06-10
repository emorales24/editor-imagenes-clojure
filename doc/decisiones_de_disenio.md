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

## Capas y separacion de responsabilidades

El programa sigue la separacion que pide la consigna entre **logica pura**, **E/S de archivos** e **interfaz**, repartida en namespaces:

- **`filters.clj`**: logica pura de procesamiento (filtros y pipeline). No conoce a ningun otro namespace.
- **`io.clj`**: entrada/salida de archivos. Lee y escribe imagenes (`ImageIO`), reconoce el formato segun la extension, aplana el alfa para JPEG y muestra los dialogos de seleccion de archivo. No depende del estado ni de los componentes de la UI: recibe la imagen y el archivo (o el frame padre) como argumentos.
- **`ui-helpers.clj`**: helpers **puros** de la capa de presentacion (`quitar-en` para editar el pipeline, `factor-de-escala` para ajustar la imagen al area visible). Al estar libres de Swing se pueden testear de forma aislada (`ui_helpers_test.clj`).
- **`app.clj`**: la interfaz grafica en si (construccion de la ventana), el estado mutable de la aplicacion, el manejo de eventos y la concurrencia. Coordina a `filters`, `io` y `ui-helpers`.
- **`core.clj`**: punto de entrada; solo lanza la interfaz.

La dependencia va siempre `core` → `app` → (`filters`, `io`, `ui-helpers`).

### Por que Swing

La interfaz usa **Swing**, que viene incluido en el JDK, a diferencia de JavaFX. Lo que evitadependencias externas: asi `lein run` funciona sin configuracion adicional.

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

Aplicar el pipeline puede tardar, asi que es mejor evitar que corra en el hilo de la interfaz (si lo hiciera, la ventana se congelaria). El esquema es:

1. Al presionar **Aplicar**, se marca la UI como "Procesando…" (botones deshabilitados y cursor de reloj).
2. El calculo se lanza en otro hilo con `future`.
3. Cuando termina, la imagen modificada vuelve al hilo de la interfaz con `SwingUtilities/invokeLater` (Swing solo permite tocar la UI desde su propio hilo).

Ademas, cada filtro paraleliza su trabajo internamente con `pmap` (una fila por tarea), aprovechando todos los cores. Es seguro porque cada pixel de salida se calcula leyendo la imagen original, que es inmutable durante ese filtro.
