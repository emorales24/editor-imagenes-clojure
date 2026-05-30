(ns tp2-2026c1-morales-giannotti.filters
  (:import [java.awt.image BufferedImage]))

(defn limitar-canal
  "Limita un valor numerico al rango valido de un canal RGB."
  [valor]
  (cond
    (< valor 0) 0
    (> valor 255) 255
    :else (int valor)))

;; Canal seria cada componente de color (R, G, B) y el canal alfa (A),
;; que representa la transparencia.
(defn argb->canales
  "Convierte un entero ARGB en un mapa de canales."
  [argb]
  {:a (bit-and (unsigned-bit-shift-right argb 24) 0xff)
   :r (bit-and (unsigned-bit-shift-right argb 16) 0xff)
   :g (bit-and (unsigned-bit-shift-right argb 8) 0xff)
   :b (bit-and argb 0xff)})

(defn canales->argb
  "Convierte canales ARGB en el entero usado por BufferedImage."
  [{:keys [a r g b]}]
  (unchecked-int
   (bit-or (bit-shift-left (limitar-canal (or a 255)) 24)
           (bit-shift-left (limitar-canal r) 16)
           (bit-shift-left (limitar-canal g) 8)
           (limitar-canal b))))

(defn- copiar-imagen-compatible
  [^BufferedImage imagen]
  (BufferedImage. (.getWidth imagen) (.getHeight imagen) BufferedImage/TYPE_INT_ARGB))

(defn mapear-pixeles
  "Aplica una funcion pura de pixel a toda la imagen y devuelve una imagen nueva.
  La funcion recibe un mapa {:a :r :g :b} y debe devolver otro mapa equivalente."
  [funcion-pixel ^BufferedImage imagen]
  (let [ancho (.getWidth imagen)
        alto (.getHeight imagen)
        filas (doall
               (pmap
                (fn [y]
                  (int-array
                   (map (fn [x]
                          (-> imagen
                              (.getRGB x y)
                              argb->canales
                              funcion-pixel
                              canales->argb))
                        (range ancho))))
                (range alto)))
        ^BufferedImage salida (copiar-imagen-compatible imagen)]
    (doseq [[y ^ints fila] (map-indexed vector filas)]
      (.setRGB salida 0 y ancho 1 fila 0 ancho))
    salida))

(defn invertir
  "Invierte los colores de la imagen."
  [imagen]
  (mapear-pixeles
   (fn [{:keys [a r g b]}]
     {:a a :r (- 255 r) :g (- 255 g) :b (- 255 b)})
   imagen))

(defn desaturar
  "Convierte la imagen a escala de grises usando el promedio de RGB."
  [imagen]
  (mapear-pixeles
   (fn [{:keys [a r g b]}]
     (let [gris (quot (+ r g b) 3)]
       {:a a :r gris :g gris :b gris}))
   imagen))

(defn brillo
  "Ajusta el brillo de la imagen sumando delta a cada canal RGB."
  ([imagen]
   (brillo 35 imagen))
  ([delta imagen]
   (mapear-pixeles
    (fn [{:keys [a r g b]}]
      {:a a
       :r (+ r delta)
       :g (+ g delta)
       :b (+ b delta)})
    imagen)))

(defn- coordenada-valida
  [valor limite-exclusivo]
  (-> valor
      (max 0)
      (min (dec limite-exclusivo))))

;; Lee un pixel de la imagen y lo pasa al mapa {:a :r :g :b}.
(defn- canales-del-pixel
  [^BufferedImage imagen x y]
  (argb->canales (.getRGB imagen x y)))

;; Calcula la posicion real de un vecino dentro de la imagen.
(defn- coordenada-vecina
  [posicion indice-kernel radio limite]
  (coordenada-valida (+ posicion (- indice-kernel radio)) limite))

;; Suma el aporte de un vecino multiplicado por el peso del kernel.
(defn- sumar-vecino
  [^BufferedImage imagen kernel radio ancho alto x y acumulado ky kx]
  (let [origen-x (coordenada-vecina x kx radio ancho)
        origen-y (coordenada-vecina y ky radio alto)
        peso (get-in kernel [ky kx])
        {:keys [r g b]} (canales-del-pixel imagen origen-x origen-y)]
    (-> acumulado
        (update :r + (* peso r))
        (update :g + (* peso g))
        (update :b + (* peso b)))))

;; Recorre una fila del kernel para un pixel dado.
(defn- sumar-fila-del-kernel
  [imagen kernel radio ancho alto x y acumulado ky]
  (reduce
   (fn [acumulado-interno kx]
     (sumar-vecino imagen kernel radio ancho alto x y acumulado-interno ky kx))
   acumulado
   (range (count kernel))))

;; Acumula todos los vecinos que participan en la convolucion de un pixel.
(defn- sumar-vecinos
  [imagen kernel radio ancho alto x y]
  (reduce
   (fn [acumulado ky]
     (sumar-fila-del-kernel imagen kernel radio ancho alto x y acumulado ky))
   {:r 0.0 :g 0.0 :b 0.0}
   (range (count kernel))))

;; Normaliza la suma final y conserva el alfa original.
(defn- aplicar-divisor-y-offset
  [sumas divisor offset alfa]
  {:a alfa
   :r (+ offset (/ (:r sumas) divisor))
   :g (+ offset (/ (:g sumas) divisor))
   :b (+ offset (/ (:b sumas) divisor))})

;; Calcula el nuevo valor de un pixel despues de aplicar el kernel.
(defn- pixel-convolucionado
  [imagen kernel divisor offset ancho alto radio x y]
  (let [{:keys [a]} (canales-del-pixel imagen x y)
        sumas (sumar-vecinos imagen kernel radio ancho alto x y)]
    (canales->argb (aplicar-divisor-y-offset sumas divisor offset a))))

;; Calcula una fila completa, lo que permite paralelizar por filas.
(defn- convolucionar-fila
  [imagen kernel divisor offset ancho alto radio y]
  (int-array
   (map (fn [x]
          (pixel-convolucionado imagen kernel divisor offset ancho alto radio x y))
        (range ancho))))

(defn convolucionar
  "Aplica un kernel cuadrado a la imagen y devuelve una imagen nueva.
  Los bordes se resuelven usando la coordenada valida mas cercana."
  [kernel divisor offset ^BufferedImage imagen]
  (let [ancho (.getWidth imagen)
        alto (.getHeight imagen)
        tamanio-kernel (count kernel)
        radio (quot tamanio-kernel 2)
        ;; Cada fila se calcula en paralelo leyendo siempre la imagen original.
        filas (doall
               (pmap
                (fn [y]
                  (convolucionar-fila imagen kernel divisor offset ancho alto radio y))
                (range alto)))
        ^BufferedImage salida (copiar-imagen-compatible imagen)]
    ;; La escritura de la imagen final se hace al terminar los calculos.
    (doseq [[y ^ints fila] (map-indexed vector filas)]
      (.setRGB salida 0 y ancho 1 fila 0 ancho))
    salida))

(defn difuminar
  "Difumina la imagen con un kernel caja de 3x3."
  [imagen]
  (convolucionar [[1 1 1]
                  [1 1 1]
                  [1 1 1]]
                 9
                 0
                 imagen))

(def filtros
  [{:id :desaturar :label "Desaturar" :funcion desaturar}
   {:id :invertir :label "Invertir" :funcion invertir}
   {:id :difuminar :label "Difuminado" :funcion difuminar}
   {:id :brillo :label "Brillo +" :funcion brillo}])

(def filtros-por-id
  (into {} (map (juxt :id identity) filtros)))

(defn nombre-filtro
  [id-filtro]
  (get-in filtros-por-id [id-filtro :label] (name id-filtro)))

(defn funcion-filtro
  [id-filtro]
  (or (get-in filtros-por-id [id-filtro :funcion])
      (throw (ex-info "Filtro desconocido" {:id-filtro id-filtro}))))

(defn aplicar-pipeline
  "Aplica todos los filtros indicados, en orden, sobre la imagen recibida."
  [imagen pipeline]
  (reduce (fn [imagen-actual id-filtro]
            ((funcion-filtro id-filtro) imagen-actual))
          imagen
          pipeline))
