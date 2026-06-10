(ns tp2-2026c1-morales-giannotti.io
  "Capa de entrada/salida de archivos: leer y escribir imagenes desde disco y
  los dialogos de seleccion de archivo. Se mantiene separada de la interfaz
  (app) y de la logica pura (filters), siguiendo la separacion que pide la
  consigna entre logica pura, E/S de archivos e interfaz."
  (:require [clojure.string :as cadena])
  (:import [javax.swing JFileChooser JFrame]
           [javax.swing.filechooser FileNameExtensionFilter]
           [java.awt Color]
           [java.awt.image BufferedImage]
           [javax.imageio ImageIO]
           [java.io File]))

(defn formato-de-archivo
  "Deduce el formato de imagen a partir de la extension del archivo."
  [^File archivo]
  (let [nombre (.getName archivo)
        punto (.lastIndexOf nombre ".")]
    (if (neg? punto)
      "png"
      (cadena/lower-case (subs nombre (inc punto))))))

(defn preparar-para-formato
  "JPEG no soporta canal alfa, asi que para ese formato se aplana la imagen
  sobre un fondo blanco. Para el resto se devuelve la imagen tal cual."
  [^BufferedImage imagen formato]
  (if (contains? #{"jpg" "jpeg"} formato)
    (let [copia (BufferedImage. (.getWidth imagen) (.getHeight imagen)
                                BufferedImage/TYPE_INT_RGB)
          lienzo (.createGraphics copia)]
      (.drawImage lienzo imagen 0 0 Color/WHITE nil)
      (.dispose lienzo)
      copia)
    imagen))

(defn leer-imagen
  "Lee una imagen desde disco. Devuelve un BufferedImage o nil si no se pudo."
  [^File archivo]
  (ImageIO/read archivo))

(defn escribir-imagen!
  "Escribe la imagen en el archivo dado, eligiendo el formato segun la extension."
  [^BufferedImage imagen ^File archivo]
  (let [formato (formato-de-archivo archivo)
        ^BufferedImage salida (preparar-para-formato imagen formato)]
    (ImageIO/write salida ^String formato archivo)))

(defn elegir-archivo-abrir
  "Abre el dialogo para elegir una imagen a cargar. Devuelve el File o nil."
  [^JFrame padre]
  (let [selector (JFileChooser.)]
    (.setFileFilter selector
                    (FileNameExtensionFilter. "Imagenes (PNG, JPG)"
                                              (into-array String ["png" "jpg" "jpeg"])))
    (when (= JFileChooser/APPROVE_OPTION (.showOpenDialog selector padre))
      (.getSelectedFile selector))))

(defn elegir-archivo-guardar
  "Abre el dialogo para elegir donde guardar. Devuelve el File o nil."
  [^JFrame padre]
  (let [selector (JFileChooser.)]
    (when (= JFileChooser/APPROVE_OPTION (.showSaveDialog selector padre))
      (.getSelectedFile selector))))
