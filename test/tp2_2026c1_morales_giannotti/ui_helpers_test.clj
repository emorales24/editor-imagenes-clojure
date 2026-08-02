(ns tp2-2026c1-morales-giannotti.ui-helpers-test
  (:require [clojure.test :refer :all]
            [tp2-2026c1-morales-giannotti.ui-helpers :as ui]))

(deftest quitar-en-elimina-el-elemento-del-indice
  (is (= [:a :c] (ui/quitar-en [:a :b :c] 1)))
  (is (= [:b :c] (ui/quitar-en [:a :b :c] 0)))
  (is (= [:a :b] (ui/quitar-en [:a :b :c] 2))))

(deftest quitar-en-sobre-un-solo-elemento-deja-vacio
  (is (= [] (ui/quitar-en [:a] 0))))

(deftest factor-de-escala-no-agranda-imagenes-chicas
  (is (= 1.0 (ui/factor-de-escala 100 100 500 500))))

(deftest factor-de-escala-reduce-cuando-el-ancho-es-el-limitante
  ;; imagen 1000x500 en area 500x500 -> manda el ancho -> 0.5
  (is (= 0.5 (ui/factor-de-escala 1000 500 500 500))))

(deftest factor-de-escala-reduce-cuando-el-alto-es-el-limitante
  ;; imagen 200x1000 en area 500x500 -> manda el alto -> 0.5
  (is (= 0.5 (ui/factor-de-escala 200 1000 500 500))))
