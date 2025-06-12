(ns scenarist.db-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [datascript.core :as d]
            [scenarist.db :as db]))

(defn create-test-db []
  (let [conn (d/create-conn db/schema)]
    ;; Инициализация игрового состояния
    (d/transact! conn
      [{:game/id :main-game
        :game/current-line 0
        :game/text-speed :medium
        :game/displayed-text ""
        :game/is-typing false}])
    conn))

(defn add-test-scene [conn scene-id]
  (d/transact! conn
    [{:db/id -1
      :line/id :line1
      :line/text "Первая строка диалога"
      :line/speaker "Персонаж 1"
      :line/position 0}
     {:db/id -2
      :line/id :line2
      :line/text "Вторая строка диалога"
      :line/speaker "Персонаж 2"
      :line/position 1}
     {:db/id -3
      :scene/id scene-id
      :scene/name "Тестовая сцена"
      :scene/background "/images/test-bg.jpg"
      :scene/music "/audio/test-music.mp3"
      :scene/lines [-1 -2]}]))

(deftest test-schema
  (testing "Создание БД со схемой"
    (let [conn (create-test-db)
          db @conn]
      (is (= (count (d/datoms db :eavt)) 5))
      (is (some? (db/game-state db))))))

(deftest test-game-state-queries
  (testing "Запрос состояния игры"
    (let [conn (create-test-db)
          db @conn
          state (db/game-state db)]
      (is (= (:game/current-line state) 0))
      (is (= (:game/text-speed state) :medium))
      (is (= (:game/displayed-text state) ""))
      (is (= (:game/is-typing state) false)))))

(deftest test-scene-operations
  (testing "Добавление и запрос сцены"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (add-test-scene db/conn :test-scene)
            _ (db/set-current-scene! :test-scene)
            db @db/conn
            scene-id (db/current-scene db)
            scene (d/entity db scene-id)
            lines (db/scene-lines db scene-id)]
        
        (is (= (:scene/name scene) "Тестовая сцена"))
        (is (= (:scene/background scene) "/images/test-bg.jpg"))
        (is (= (count lines) 2))
        (is (= (:line/text (first lines)) "Первая строка диалога"))
        (is (= (:line/speaker (second lines)) "Персонаж 2"))))))

(deftest test-text-operations
  (testing "Обновление отображаемого текста"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (db/update-displayed-text! "Новый текст")
            db @db/conn
            state (db/game-state db)]
        (is (= (:game/displayed-text state) "Новый текст")))))
  
  (testing "Установка состояния печати"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (db/set-typing-state! true)
            db @db/conn
            state (db/game-state db)]
        (is (= (:game/is-typing state) true))))))

(deftest test-navigation
  (testing "Переход к следующей строке"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (add-test-scene db/conn :test-scene)
            _ (db/set-current-scene! :test-scene)
            _ (db/advance-line!)
            db @db/conn
            state (db/game-state db)]
        (is (= (:game/current-line state) 1))
        (is (= (:game/displayed-text state) ""))
        (is (= (:game/is-typing state) false)))))

(deftest test-text-speed
  (testing "Изменение скорости текста"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (db/set-text-speed! :fast)
            db @db/conn
            state (db/game-state db)]
        (is (= (:game/text-speed state) :fast))))))

(deftest test-scene-switching
  (testing "Переключение между сценами"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (add-test-scene db/conn :scene1)
            _ (add-test-scene db/conn :scene2)
            _ (db/set-current-scene! :scene1)
            db1 @db/conn
            scene1-id (db/current-scene db1)
            _ (db/advance-line!)
            _ (db/set-current-scene! :scene2)
            db2 @db/conn
            scene2-id (db/current-scene db2)
            state (db/game-state db2)]
        
        (is (not= scene1-id scene2-id))
        (is (= (:game/current-line state) 0))
        (is (= (:game/displayed-text state) ""))))))

(deftest test-complex-scenario
  (testing "Сложный сценарий с несколькими операциями"
    (with-redefs [db/conn (create-test-db)]
      (let [_ (add-test-scene db/conn :intro)]
        
        ;; Устанавливаем сцену
        (db/set-current-scene! :intro)
        
        ;; Начинаем печатать первую строку
        (db/set-typing-state! true)
        (db/update-displayed-text! "Первая")
        
        (let [db @db/conn
              state (db/game-state db)]
          (is (= (:game/displayed-text state) "Первая"))
          (is (= (:game/is-typing state) true)))
        
        ;; Завершаем печать
        (db/update-displayed-text! "Первая строка диалога")
        (db/set-typing-state! false)
        
        ;; Переходим к следующей строке
        (db/advance-line!)
        
        (let [db @db/conn
              state (db/game-state db)]
          (is (= (:game/current-line state) 1))
          (is (= (:game/displayed-text state) ""))))))))

;; Функция для запуска всех тестов
(defn run-tests []
  (cljs.test/run-tests))