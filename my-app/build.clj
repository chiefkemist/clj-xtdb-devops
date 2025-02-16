(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'my-app)
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file "target/my_app.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile-src []
  (clean nil)
  (println "AOT compiling source...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :ns-compile ['my-app.handler]}))

(defn jar [_]
  (compile-src)
  (b/uber {:class-dir class-dir
           :uber-file jar-file
           :basis basis
           :main 'my-app.handler})
  (println "Uberjar created:" jar-file))

(defn -main [& args]
  (case (first args)
    "clean" (clean args)
    "jar" (jar args)
    (println "Usage: build [clean|jar]")))
