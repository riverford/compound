(defproject riverford/compound "0.4.0-SNAPSHOT"
  :description "A micro structure for reagent data"
  :url "https://github.com/riverford/compound"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-beta1"]
                 [org.clojure/spec.alpha "0.1.134"]]
  :publish {:site "compound"
            :theme "bolton"
            :output "docs"
            :template {:author "Daniel Neal"
                       :logo-white "img/compound_small.png"
                       :email "danielneal@riverford.co.uk"}
            :files {"index" {:input "test/compound/docs.cljc"
                             :site "compound"
                             :title "core"
                             :subtitle "api docs"}}}
  :profiles {:dev {:dependencies [[im.chit/lucid.publish "1.3.13"]]}})
