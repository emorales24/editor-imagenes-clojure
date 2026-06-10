(ns tp2-2026c1-morales-giannotti.app
  "Capa de presentacion: interfaz grafica (Swing), estado de la aplicacion,
  manejo de eventos y concurrencia. La E/S de archivos vive en el namespace io,
  los helpers puros de UI en ui-helpers y la logica de filtros en filters."
  (:require [tp2-2026c1-morales-giannotti.filters :as filtros]
            [tp2-2026c1-morales-giannotti.io :as io]
            [tp2-2026c1-morales-giannotti.ui-helpers :as ui])
  (:import [javax.swing JFrame JPanel JButton JLabel JComboBox JList JScrollPane
            DefaultListModel JMenuBar JMenu JMenuItem SwingUtilities
            ImageIcon BorderFactory BoxLayout AbstractButton JProgressBar JViewport]
           [java.awt BorderLayout Dimension Cursor Image]
           [java.awt.event ActionListener ComponentAdapter]
           [java.awt.image BufferedImage]))

;; -----------------------------------------------------------------------------
;; Estado
;; -----------------------------------------------------------------------------
;; El estado de la aplicacion es mutable pero vive en un unico atom, separado de
;; los filtros (que son puros). Guardamos la imagen original cargada, la imagen
;; que se esta mostrando, el archivo asociado y el pipeline (vector de ids).

(def estado
  (atom {:imagen-original nil
         :imagen-actual nil
         :archivo nil
         :pipeline []}))

;; Referencias a los componentes Swing que necesitamos actualizar desde los
;; manejadores de eventos. Se completa al construir la ventana.
(def componentes (atom {}))

(defn- imagen-actual []
  (:imagen-actual @estado))

;; -----------------------------------------------------------------------------
;; Actualizacion de la interfaz
;; -----------------------------------------------------------------------------

(defn- refrescar-lista!
  "Reconstruye la lista visible del pipeline a partir del estado."
  []
  (let [^DefaultListModel modelo (:modelo-pipeline @componentes)]
    (.clear modelo)
    (doseq [id-filtro (:pipeline @estado)]
      (.addElement modelo (filtros/nombre-filtro id-filtro)))))

(defn- ^Image imagen-escalada
  "Devuelve la imagen ajustada al area disponible. Si ya entra, la deja igual."
  [^BufferedImage imagen ancho-disp alto-disp]
  (let [ancho-img (.getWidth imagen)
        alto-img (.getHeight imagen)
        factor (ui/factor-de-escala ancho-img alto-img ancho-disp alto-disp)]
    (if (>= factor 1.0)
      imagen
      (.getScaledInstance imagen
                          (int (* ancho-img factor))
                          (int (* alto-img factor))
                          Image/SCALE_SMOOTH))))

(defn- redibujar-imagen!
  "Vuelve a mostrar la imagen actual escalada al tamano del area de visualizacion.
  Se invoca al cargar/procesar una imagen y cada vez que cambia el tamano de la
  ventana."
  []
  (when-let [^BufferedImage imagen (imagen-actual)]
    (let [^JScrollPane scroll (:scroll-imagen @componentes)
          ^JViewport vista (.getViewport scroll)
          ^Dimension area (.getExtentSize vista)
          ancho-disp (max 1 (.width area))
          alto-disp (max 1 (.height area))
          ^JLabel etiqueta (:etiqueta-imagen @componentes)]
      (.setText etiqueta nil)
      (.setIcon etiqueta (ImageIcon. (imagen-escalada imagen ancho-disp alto-disp)))
      (.revalidate etiqueta)
      (.repaint etiqueta))))

(defn- marcar-procesando!
  "Refleja en la UI si hay una tarea en curso: deshabilita los botones,
  cambia el texto de estado, el cursor a reloj y muestra la barra de progreso."
  [procesando?]
  (let [{:keys [frame estado-label boton-agregar boton-quitar
                boton-aplicar boton-reset barra-progreso]} @componentes
        habilitado? (not procesando?)]
    (.setEnabled ^JButton boton-agregar habilitado?)
    (.setEnabled ^JButton boton-quitar habilitado?)
    (.setEnabled ^JButton boton-aplicar habilitado?)
    (.setEnabled ^JButton boton-reset habilitado?)
    (.setText ^JLabel estado-label (if procesando? "Procesando…" "Listo"))
    (.setValue ^JProgressBar barra-progreso 0)
    (.setVisible ^JProgressBar barra-progreso procesando?)
    (.setCursor ^JFrame frame
                (Cursor/getPredefinedCursor
                 (if procesando? Cursor/WAIT_CURSOR Cursor/DEFAULT_CURSOR)))))

(defn- actualizar-progreso!
  "Mueve la barra de progreso segun cuantos filtros del pipeline ya se aplicaron."
  [completados total]
  (let [^JProgressBar barra (:barra-progreso @componentes)]
    (.setMaximum barra (max 1 total))
    (.setValue barra completados)))

;; -----------------------------------------------------------------------------
;; Entrada / salida de archivos
;; -----------------------------------------------------------------------------
;; La E/S concreta (leer/escribir y dialogos) vive en el namespace io. Aca solo
;; se la coordina con el estado de la aplicacion.

(defn- guardar-en!
  "Escribe la imagen actual en el archivo dado y lo recuerda como archivo activo."
  [archivo]
  (io/escribir-imagen! (imagen-actual) archivo)
  (swap! estado assoc :archivo archivo))

;; -----------------------------------------------------------------------------
;; Procesamiento en segundo plano (concurrencia)
;; -----------------------------------------------------------------------------
;; "Aplicar" no puede bloquear la interfaz. El calculo corre en otro hilo con
;; `future`; cuando termina, el pintado vuelve al hilo de la UI con invokeLater.

(defn- aplicar-listo!
  "Se ejecuta en el hilo de la UI al terminar el procesamiento."
  [resultado]
  (swap! estado assoc :imagen-actual resultado)
  (redibujar-imagen!)
  (marcar-procesando! false))

(defn- aplicar-paso
  "Aplica un filtro y reporta el avance a la barra de progreso. Pensada para
  usarse como funcion de reduce sobre el pipeline (recibe el total fijo)."
  [total imagen [indice id-filtro]]
  (let [resultado ((filtros/funcion-filtro id-filtro) imagen)]
    (SwingUtilities/invokeLater (partial actualizar-progreso! (inc indice) total))
    resultado))

(defn- procesar-en-segundo-plano
  "Aplica el pipeline sobre la imagen original (en un hilo aparte), informando el
  avance filtro a filtro, y delega el refresco de la UI al hilo de la interfaz."
  []
  (let [{:keys [imagen-original pipeline]} @estado
        total (count pipeline)
        resultado (reduce (partial aplicar-paso total)
                          imagen-original
                          (map-indexed vector pipeline))]
    (SwingUtilities/invokeLater (partial aplicar-listo! resultado))))

;; -----------------------------------------------------------------------------
;; Manejadores de eventos
;; -----------------------------------------------------------------------------

(defn abrir []
  (when-let [archivo (io/elegir-archivo-abrir (:frame @componentes))]
    (when-let [imagen (io/leer-imagen archivo)]
      (swap! estado assoc
             :imagen-original imagen
             :imagen-actual imagen
             :archivo archivo
             :pipeline [])
      (refrescar-lista!)
      (redibujar-imagen!))))

(defn guardar-como []
  (when (imagen-actual)
    (when-let [archivo (io/elegir-archivo-guardar (:frame @componentes))]
      (guardar-en! archivo))))

(defn guardar []
  (when (imagen-actual)
    (if-let [archivo (:archivo @estado)]
      (guardar-en! archivo)
      (guardar-como))))

(defn agregar
  "Agrega al final del pipeline el filtro elegido en el selector."
  []
  (let [^JComboBox selector (:selector @componentes)
        indice (.getSelectedIndex selector)]
    (when (>= indice 0)
      (let [id-filtro (:id (nth filtros/filtros indice))]
        (swap! estado update :pipeline conj id-filtro)
        (refrescar-lista!)))))

(defn quitar
  "Quita del pipeline el paso seleccionado en la lista."
  []
  (let [^JList lista (:lista @componentes)
        indice (.getSelectedIndex lista)]
    (when (>= indice 0)
      (swap! estado update :pipeline ui/quitar-en indice)
      (refrescar-lista!))))

(defn aplicar
  "Lanza el procesamiento del pipeline en segundo plano."
  []
  (when (:imagen-original @estado)
    (marcar-procesando! true)
    (future (procesar-en-segundo-plano))))

(defn reiniciar
  "Vacia el pipeline y restaura la imagen original."
  []
  (swap! estado assoc :pipeline [])
  (refrescar-lista!)
  (when-let [original (:imagen-original @estado)]
    (swap! estado assoc :imagen-actual original)
    (redibujar-imagen!)))

(defn salir []
  (System/exit 0))

;; -----------------------------------------------------------------------------
;; Construccion de la ventana
;; -----------------------------------------------------------------------------

(defn- conectar
  "Conecta un boton (o item de menu) con una funcion de manejo sin argumentos."
  [^AbstractButton boton manejador]
  (.addActionListener boton
                      (reify ActionListener
                        (actionPerformed [_ _] (manejador)))))

(defn- ^JComboBox crear-selector-filtros []
  (let [selector (JComboBox.)]
    (doseq [filtro filtros/filtros]
      (.addItem selector (:label filtro)))
    selector))

(defn- crear-panel-filtros
  "Panel lateral: lista del pipeline, selector y botones de accion."
  []
  (let [panel (JPanel.)
        modelo (DefaultListModel.)
        lista (JList. modelo)
        selector (crear-selector-filtros)
        boton-agregar (JButton. "Agregar")
        boton-quitar (JButton. "Quitar")
        boton-aplicar (JButton. "Aplicar")
        boton-reset (JButton. "Reset")]
    (conectar boton-agregar agregar)
    (conectar boton-quitar quitar)
    (conectar boton-aplicar aplicar)
    (conectar boton-reset reiniciar)
    (.setLayout panel (BoxLayout. panel BoxLayout/Y_AXIS))
    (.setBorder panel (BorderFactory/createTitledBorder "Filtros"))
    (.setPreferredSize panel (Dimension. 220 0))
    (doto panel
      (.add (JLabel. "Pipeline:"))
      (.add (JScrollPane. lista))
      (.add (JLabel. "Filtro a agregar:"))
      (.add selector)
      (.add boton-agregar)
      (.add boton-quitar)
      (.add boton-aplicar)
      (.add boton-reset))
    {:panel panel
     :modelo-pipeline modelo
     :lista lista
     :selector selector
     :boton-agregar boton-agregar
     :boton-quitar boton-quitar
     :boton-aplicar boton-aplicar
     :boton-reset boton-reset}))

(defn- crear-menu []
  (let [barra (JMenuBar.)
        menu (JMenu. "Archivo")
        item-abrir (JMenuItem. "Abrir")
        item-guardar (JMenuItem. "Guardar")
        item-guardar-como (JMenuItem. "Guardar como")
        item-salir (JMenuItem. "Salir")]
    (conectar item-abrir abrir)
    (conectar item-guardar guardar)
    (conectar item-guardar-como guardar-como)
    (conectar item-salir salir)
    (doto menu
      (.add item-abrir)
      (.add item-guardar)
      (.add item-guardar-como)
      (.addSeparator)
      (.add item-salir))
    (.add barra menu)
    barra))

(defn- escuchar-redimension
  "Reescala la imagen cada vez que cambia el tamano del area de visualizacion."
  [^JScrollPane scroll]
  (.addComponentListener scroll
                         (proxy [ComponentAdapter] []
                           (componentResized [_] (redibujar-imagen!)))))

(defn- ^JPanel crear-barra-estado
  "Barra inferior con el texto de estado y la barra de progreso."
  [^JLabel estado-label ^JProgressBar barra-progreso]
  (let [panel (JPanel. (BorderLayout.))]
    (.setVisible barra-progreso false)
    (.add panel estado-label BorderLayout/WEST)
    (.add panel barra-progreso BorderLayout/CENTER)
    panel))

(defn- construir-ventana []
  (let [frame (JFrame. "TP2 - Procesamiento de imagenes")
        etiqueta-imagen (doto (JLabel. "Abri una imagen para empezar (Archivo > Abrir)")
                          (.setHorizontalAlignment JLabel/CENTER))
        scroll-imagen (JScrollPane. etiqueta-imagen)
        estado-label (JLabel. "Listo")
        barra-progreso (JProgressBar.)
        panel-filtros (crear-panel-filtros)
        contenido (JPanel. (BorderLayout.))]
    (.add contenido scroll-imagen BorderLayout/CENTER)
    (.add contenido ^JPanel (:panel panel-filtros) BorderLayout/EAST)
    (.add contenido (crear-barra-estado estado-label barra-progreso) BorderLayout/SOUTH)
    (reset! componentes
            (merge panel-filtros
                   {:frame frame
                    :etiqueta-imagen etiqueta-imagen
                    :scroll-imagen scroll-imagen
                    :estado-label estado-label
                    :barra-progreso barra-progreso}))
    (escuchar-redimension scroll-imagen)
    (doto frame
      (.setJMenuBar (crear-menu))
      (.setContentPane contenido)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setSize (Dimension. 1000 700))
      (.setLocationRelativeTo nil))
    frame))

(defn- mostrar-ventana []
  (.setVisible ^JFrame (construir-ventana) true))

(defn iniciar
  "Punto de entrada de la interfaz. Construye y muestra la ventana en el hilo
  de eventos de Swing."
  []
  (SwingUtilities/invokeLater mostrar-ventana))
