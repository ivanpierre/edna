(defn read-deps-edn [aliases-to-include]
  (let [{:keys [paths deps aliases]} (-> "deps.edn" slurp clojure.edn/read-string)
        deps (->> (select-keys aliases aliases-to-include)
                  vals
                  (mapcat :extra-deps)
                  (into deps)
                  (reduce
                    (fn [deps [artifact info]]
                      (if-let [version (:mvn/version info)]
                        (conj deps
                          (transduce cat conj [artifact version]
                            (select-keys info [:scope :exclusions])))
                        deps))
                    []))]
    {:dependencies deps
     :source-paths (set paths)
     :resource-paths (set paths)}))

(let [{:keys [source-paths resource-paths dependencies]} (read-deps-edn [])]
  (set-env!
    :source-paths source-paths
    :resource-paths resource-paths
    :dependencies (into '[[adzerk/boot-cljs "2.1.5" :scope "test"]
                          [adzerk/boot-reload "0.6.0" :scope "test"]
                          [nightlight "RELEASE" :scope "test"]
                          [dynadoc "RELEASE" :scope "test"]]
                    dependencies)
    :repositories (conj (get-env :repositories)
                    ["clojars" {:url "https://clojars.org/repo/"
                                :username (System/getenv "CLOJARS_USER")
                                :password (System/getenv "CLOJARS_PASS")}])))

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-reload :refer [reload]]
  '[nightlight.boot :refer [nightlight]]
  '[dynadoc.boot :refer [dynadoc]])

(task-options!
  pom {:project 'edna
       :version "1.6.1-SNAPSHOT"
       :description "A Clojure data -> music library"
       :url "https://github.com/oakes/edna"
       :license {"Public Domain" "http://unlicense.org/UNLICENSE"}}
  push {:repo "clojars"})

(deftask run []
  (comp
    (wait)
    (with-pass-thru _
      (require 'edna.core))
    (nightlight :port 4000)))

(deftask run-docs []
  (set-env!
    :dependencies #(into (set %) (:dependencies (read-deps-edn [:cljs])))
    :resource-paths #(conj % "dev-resources"))
  (comp
    (watch)
    (reload :asset-path "dynadoc-extend")
    (cljs
      :optimizations :simple
      :compiler-options {:asset-path "/main.out"})
    (with-pass-thru _
      (require 'edna.core))
    (nightlight :port 4000)
    (dynadoc :port 5000)))

(deftask local []
  (comp (pom) (jar) (install)))

(deftask deploy []
  (comp (pom) (jar) (push)))

