(defproject riverford/compound "2018.01.26-1"
  :description "A micro structure for reagent data"
  :url "https://github.com/riverford/compound"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
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
  :profiles {:dev {:dependencies [[im.chit/lucid.publish "1.3.13"]
                                  [orchestra "2017.11.12-1"]]}})
