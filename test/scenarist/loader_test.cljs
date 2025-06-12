(ns scenarist.loader-test
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.engine.loader :as loader]
            [scenarist.engine.script :as script]))

(def valid-scenario-edn
  "{:metadata {:title \"Тестовый сценарий\"
              :author \"Тестировщик\"
              :version \"1.0\"}
    :scenes
    [{:id :scene1
      :name \"Первая сцена\"
      :background \"/images/bg1.jpg\"
      :music \"/audio/music1.mp3\"
      :lines
      [{:text \"Первая строка\"
        :speaker \"Говорящий 1\"}
       {:text \"Вторая строка\"
        :speaker nil}]}
     {:id :scene2
      :name \"Вторая сцена\"
      :background \"/images/bg2.jpg\"
      :lines
      [{:text \"Строка в другой сцене\"
        :speaker \"Говорящий 2\"}]}]}")

(def invalid-scenario-missing-id
  "{:metadata {:title \"Сломанный сценарий\"}
    :scenes
    [{:name \"Сцена без ID\"
      :lines [{:text \"Текст\"}]}]}")

(def invalid-scenario-bad-structure
  "{:metadata {:title \"Неправильная структура\"}
    :scenes \"не массив\"}")

(def malformed-edn
  "{:metadata {:title \"Незакрытая скобка\"
    :scenes [")

(deftest test-validate-scenario
  (testing "Валидация корректного сценария"
    (let [scenario (cljs.reader/read-string valid-scenario-edn)]
      (is (loader/validate-scenario scenario))))
  
  (testing "Валидация некорректных сценариев"
    (let [scenario1 (cljs.reader/read-string invalid-scenario-missing-id)]
      (is (not (loader/validate-scenario scenario1))))
    
    (let [scenario2 (cljs.reader/read-string invalid-scenario-bad-structure)]
      (is (not (loader/validate-scenario scenario2))))))

(deftest test-load-scenario-from-string
  (testing "Загрузка корректного сценария из строки"
    (let [result (loader/load-scenario-from-string valid-scenario-edn)]
      (is (:success result))
      (is (= (get-in result [:scenario :metadata :title]) "Тестовый сценарий"))
      (is (= (count (get-in result [:scenario :scenes])) 2))))
  
  (testing "Загрузка некорректного сценария"
    (let [result (loader/load-scenario-from-string invalid-scenario-missing-id)]
      (is (not (:success result)))
      (is (= (:error result) "Invalid scenario structure"))))
  
  (testing "Загрузка сломанного EDN"
    (let [result (loader/load-scenario-from-string malformed-edn)]
      (is (not (:success result)))
      (is (re-find #"Parse error" (:error result))))))

(deftest test-process-scenario
  (testing "Обработка загруженного сценария"
    (let [conn (d/create-conn db/schema)]
      (with-redefs [db/conn conn
                    println (fn [& args] nil)] ; заглушаем вывод
        (db/init-game-state!)
        
        (let [scenario (cljs.reader/read-string valid-scenario-edn)]
          (loader/process-scenario! scenario)
          
          ;; Проверяем, что сцены загружены
          (is (= (script/get-scene-count) 2))
          
          ;; Проверяем, что перешли к первой сцене
          (let [current-scene (d/entity @conn (db/current-scene @conn))]
            (is (= (:scene/id current-scene) :scene1))
            (is (= (:scene/name current-scene) "Первая сцена"))))))))

(deftest test-scenario-metadata
  (testing "Обработка метаданных сценария"
    (let [scenario (cljs.reader/read-string valid-scenario-edn)]
      (is (= (:title (:metadata scenario)) "Тестовый сценарий"))
      (is (= (:author (:metadata scenario)) "Тестировщик"))
      (is (= (:version (:metadata scenario)) "1.0")))))

(deftest test-complex-scenario
  (testing "Загрузка сложного сценария"
    (let [complex-scenario
          "{:metadata {:title \"Сложный сценарий\"
                      :author \"Автор\"
                      :version \"2.0\"
                      :description \"Сценарий с разными типами данных\"}
            :scenes
            [{:id :intro
              :name \"Вступление\"
              :background \"/bg/intro.jpg\"
              :music \"/bgm/intro.mp3\"
              :lines
              [{:text \"Текст без говорящего\"
                :speaker nil}
               {:text \"Текст с говорящим\"
                :speaker \"Персонаж\"}
               {:text \"Очень длинный текст, который может содержать специальные символы: \\\"кавычки\\\", 'апострофы', и даже \\n переносы строк!\"
                :speaker \"Рассказчик\"}]}
             {:id :middle
              :name \"Середина\"
              :background \"/bg/middle.jpg\"
              :lines
              [{:text \"Единственная строка\"
                :speaker \"Одиночка\"}]}
             {:id :end
              :name \"Конец\"
              :background \"/bg/end.jpg\"
              :music \"/bgm/end.mp3\"
              :lines
              [{:text \"Финальная строка\"
                :speaker \"Финал\"}]}]}"]
      
      (let [result (loader/load-scenario-from-string complex-scenario)]
        (is (:success result))
        (is (= (count (get-in result [:scenario :scenes])) 3))))))

(deftest test-empty-scenario
  (testing "Обработка пустого сценария"
    (let [empty-scenario
          "{:metadata {:title \"Пустой сценарий\"}
            :scenes []}"]
      
      (let [result (loader/load-scenario-from-string empty-scenario)]
        (is (:success result))
        (is (empty? (get-in result [:scenario :scenes])))))))

(deftest test-scenario-with-minimal-data
  (testing "Сценарий с минимальными данными"
    (let [minimal-scenario
          "{:metadata {:title \"Минимальный\"}
            :scenes
            [{:id :only
              :name \"Единственная сцена\"
              :lines [{:text \"Привет!\"}]}]}"]
      
      (let [result (loader/load-scenario-from-string minimal-scenario)]
        (is (:success result))
        
        (let [conn (d/create-conn db/schema)]
          (with-redefs [db/conn conn
                        println (fn [& args] nil)]
            (db/init-game-state!)
            (loader/process-scenario! (:scenario result))
            
            ;; Проверяем загрузку
            (let [current-scene (d/entity @conn (db/current-scene @conn))
                  lines (db/scene-lines @conn (:db/id current-scene))]
              (is (= (:scene/id current-scene) :only))
              (is (= (count lines) 1))
              (is (= (:line/text (first lines)) "Привет!")))))))))

(deftest test-load-scenario-async
  (testing "Асинхронная загрузка сценария (симуляция)"
    (async done
      (let [conn (d/create-conn db/schema)
            mock-fetch (fn [url]
                         (js/Promise.
                           (fn [resolve reject]
                             (if (= url "/test/scenario.edn")
                               (resolve #js {:text (fn [] valid-scenario-edn)})
                               (reject "File not found")))))]
        
        (with-redefs [db/conn conn
                      js/fetch mock-fetch
                      println (fn [& args] nil)]
          (db/init-game-state!)
          
          (-> (loader/load-scenario! "/test/scenario.edn")
              (.then (fn []
                       ;; Даём время на обработку
                       (js/setTimeout
                         (fn []
                           ;; Проверяем результат
                           (is (= (script/get-scene-count) 2))
                           (done))
                         100)))))))))

(deftest test-scenario-error-handling
  (testing "Обработка ошибок при загрузке"
    (let [scenarios-with-errors
          [;; Отсутствует обязательное поле
           "{:metadata {:title \"Без сцен\"}}"
           
           ;; Неправильный тип ID
           "{:metadata {:title \"Неправильный ID\"}
             :scenes [{:id \"string-id\" :name \"Сцена\" :lines []}]}"
           
           ;; Пустые строки
           "{:metadata {:title \"Пустые строки\"}
             :scenes [{:id :scene :name \"Сцена\" :lines []}]}"]]
      
      (doseq [scenario-str scenarios-with-errors]
        (let [result (loader/load-scenario-from-string scenario-str)]
          (is (not (:success result))))))))

;; Функция для запуска всех тестов загрузчика
(defn run-tests []
  (cljs.test/run-tests))