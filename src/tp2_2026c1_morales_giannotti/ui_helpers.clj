(ns tp2-2026c1-morales-giannotti.ui-helpers
  "Helpers puros de la capa de presentacion, sin dependencias de Swing.
  Estan separados de app para poder testearlos de forma aislada.")

(defn quitar-en
  "Devuelve el vector sin el elemento que esta en el indice dado."
  [coleccion indice]
  (vec (concat (subvec coleccion 0 indice)
               (subvec coleccion (inc indice)))))

(defn factor-de-escala
  "Factor para que la imagen entre en el area disponible conservando proporciones.
  Solo reduce (nunca agranda): las imagenes mas chicas se muestran a tamano real."
  [ancho-img alto-img ancho-disp alto-disp]
  (min 1.0
       (/ (double ancho-disp) ancho-img)
       (/ (double alto-disp) alto-img)))
