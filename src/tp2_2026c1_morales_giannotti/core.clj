(ns tp2-2026c1-morales-giannotti.core
  "Punto de entrada del programa. Solo lanza la interfaz grafica."
  (:require [tp2-2026c1-morales-giannotti.app :as app])
  (:gen-class))

(defn -main
  "Inicia la aplicacion de procesamiento de imagenes."
  [& _args]
  (app/iniciar))
