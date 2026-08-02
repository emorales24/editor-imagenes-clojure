(ns tp2-2026c1-morales-giannotti.filters-test
  (:require [clojure.test :refer :all]
            [tp2-2026c1-morales-giannotti.filters :as filters])
  (:import [java.awt.image BufferedImage]))

(defn- imagen-de-prueba
  [width height pixels]
  (let [image (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)]
    (doseq [[[x y] channels] pixels]
      (.setRGB image x y (filters/canales->argb channels)))
    image))

(defn- pixel-en
  [^BufferedImage image x y]
  (filters/argb->canales (.getRGB image x y)))

(deftest limitar-canal-deja-valores-dentro-del-rango-rgb
  (is (= 0 (filters/limitar-canal -10)))
  (is (= 120 (filters/limitar-canal 120)))
  (is (= 255 (filters/limitar-canal 300))))

(deftest invertir-genera-colores-negativos
  (let [image (imagen-de-prueba 1 1 {[0 0] {:a 255 :r 10 :g 20 :b 30}})
        result (filters/invertir image)]
    (is (= {:a 255 :r 245 :g 235 :b 225}
           (pixel-en result 0 0)))))

(deftest desaturar-iguala-los-canales-rgb
  (let [image (imagen-de-prueba 1 1 {[0 0] {:a 255 :r 100 :g 150 :b 200}})
        result (filters/desaturar image)
        result-pixel (pixel-en result 0 0)]
    (is (= {:a 255 :r 150 :g 150 :b 150}
           result-pixel))))

(deftest brillo-limita-los-valores-de-los-canales
  (let [image (imagen-de-prueba 1 1 {[0 0] {:a 255 :r 240 :g 10 :b 120}})
        result (filters/brillo 35 image)]
    (is (= {:a 255 :r 255 :g 45 :b 155}
           (pixel-en result 0 0)))))

(deftest aplicar-pipeline-respeta-el-orden
  (let [image (imagen-de-prueba 1 1 {[0 0] {:a 255 :r 10 :g 20 :b 30}})
        result (filters/aplicar-pipeline image [:invertir :brillo])]
    (is (= {:a 255 :r 255 :g 255 :b 255}
           (pixel-en result 0 0)))))

(deftest difuminar-usa-pixeles-vecinos
  (let [image (imagen-de-prueba 3 3 {[0 0] {:a 255 :r 0 :g 0 :b 0}
                               [1 0] {:a 255 :r 0 :g 0 :b 0}
                               [2 0] {:a 255 :r 0 :g 0 :b 0}
                               [0 1] {:a 255 :r 0 :g 0 :b 0}
                               [1 1] {:a 255 :r 90 :g 90 :b 90}
                               [2 1] {:a 255 :r 0 :g 0 :b 0}
                               [0 2] {:a 255 :r 0 :g 0 :b 0}
                               [1 2] {:a 255 :r 0 :g 0 :b 0}
                               [2 2] {:a 255 :r 0 :g 0 :b 0}})
        result (filters/difuminar image)]
    (is (= {:a 255 :r 10 :g 10 :b 10}
           (pixel-en result 1 1)))))
