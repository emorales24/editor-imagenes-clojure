(defproject tp2-2026c1-morales-giannotti "0.1.0-SNAPSHOT"
  :description "Procesamiento de imagenes con filtros componibles (TP2 Paradigmas, FIUBA)"
  :url "https://github.com/paradigmas-tb025-essaya"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :main ^:skip-aot tp2-2026c1-morales-giannotti.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
