(ns scenarist.script-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.engine.script :as script]))

(defn create-test-environment []
  (let [conn (d/create-conn db/schema)]
    (with-redefs [db/conn conn]
      (db/init-game-state!)
      conn)))

(deftest test-add-scene
  (testing "Добавление новой сцены"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/add-scene!
          {:id :test-scene
           :name "Тестовая сцена"
           :background "/test-bg.jpg"
           :music "/test-music.mp3"
           :lines
           [{:text "Первая строка"
             :speaker "Говорящий 1"}
            {:text "Вторая строка"
             :speaker "Говорящий 2"}]})
        
        (let [db @conn
              scenes (script/get-all-scenes)]
          (is (= (count scenes) 1))
          (is (= (:scene/id (first scenes)) :test-scene))
          (is (= (:scene/name (first scenes)) "Тестовая сцена")))))))

(deftest test-scene-exists
  (testing "Проверка существования сцены"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (is (not (script/scene-exists? :nonexistent)))
        
        (script/add-scene!
          {:id :existing-scene
           :name "Существующая сцена"
           :background "/bg.jpg"
           :lines [{:text "Текст" :speaker nil}]})
        
        (is (script/scene-exists? :existing-scene))
        (is (not (script/scene-exists? :still-nonexistent)))))))

(deftest test-jump-to-scene
  (testing "Переход к сцене"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/add-scene!
          {:id :scene1
           :name "Первая сцена"
           :background "/bg1.jpg"
           :lines [{:text "Текст первой сцены" :speaker nil}]})
        
        (script/add-scene!
          {:id :scene2
           :name "Вторая сцена"
           :background "/bg2.jpg"
           :lines [{:text "Текст второй сцены" :speaker nil}]})
        
        ;; Переходим к первой сцене
        (script/jump-to-scene! :scene1)
        (let [db @conn
              current-scene-id (db/current-scene db)
              scene (d/entity db current-scene-id)]
          (is (= (:scene/id scene) :scene1)))
        
        ;; Переходим ко второй сцене
        (script/jump-to-scene! :scene2)
        (let [db @conn
              current-scene-id (db/current-scene db)
              scene (d/entity db current-scene-id)]
          (is (= (:scene/id scene) :scene2)))))))

(deftest test-scene-count
  (testing "Подсчёт количества сцен"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (is (= (script/get-scene-count) 0))
        
        (script/add-scene!
          {:id :scene1
           :name "Сцена 1"
           :background "/bg.jpg"
           :lines [{:text "Текст" :speaker nil}]})
        
        (is (= (script/get-scene-count) 1))
        
        (script/add-scene!
          {:id :scene2
           :name "Сцена 2"
           :background "/bg.jpg"
           :lines [{:text "Текст" :speaker nil}]})
        
        (is (= (script/get-scene-count) 2))))))

(deftest test-advance-scene
  (testing "Переход к следующей сцене"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/add-scene!
          {:id :scene1
           :name "Первая сцена"
           :background "/bg1.jpg"
           :lines [{:text "Текст 1" :speaker nil}]})
        
        (script/add-scene!
          {:id :scene2
           :name "Вторая сцена"
           :background "/bg2.jpg"
           :lines [{:text "Текст 2" :speaker nil}]})
        
        ;; Переходим к первой сцене
        (script/jump-to-scene! :scene1)
        
        ;; Переходим к следующей
        (script/advance-scene!)
        
        (let [db @conn
              current-scene-id (db/current-scene db)
              scene (d/entity db current-scene-id)]
          (is (= (:scene/id scene) :scene2)))))))

(deftest test-advance-scene-at-end
  (testing "Переход к следующей сцене в конце игры"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn
                    println (fn [& args] nil)] ; заглушаем println
        (script/add-scene!
          {:id :final-scene
           :name "Финальная сцена"
           :background "/bg.jpg"
           :lines [{:text "Конец" :speaker nil}]})
        
        (script/jump-to-scene! :final-scene)
        
        ;; Пытаемся перейти дальше
        (script/advance-scene!)
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/displayed-text state) "Конец игры"))
          (is (= (:game/is-typing state) false)))))))

(deftest test-execute-command-scene
  (testing "Выполнение команды создания сцены"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/execute-command
          {:type :scene
           :id :cmd-scene
           :name "Командная сцена"
           :background "/cmd-bg.jpg"
           :music "/cmd-music.mp3"
           :lines
           [{:text "Командный текст"
             :speaker "Командный говорящий"}]})
        
        (is (script/scene-exists? :cmd-scene))
        (let [scenes (script/get-all-scenes)
              scene (first (filter #(= (:scene/id %) :cmd-scene) scenes))]
          (is (= (:scene/name scene) "Командная сцена")))))))

(deftest test-execute-command-jump
  (testing "Выполнение команды перехода"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/add-scene!
          {:id :target-scene
           :name "Целевая сцена"
           :background "/target-bg.jpg"
           :lines [{:text "Целевой текст" :speaker nil}]})
        
        (script/execute-command
          {:type :jump
           :target :target-scene})
        
        (let [db @conn
              current-scene-id (db/current-scene db)
              scene (d/entity db current-scene-id)]
          (is (= (:scene/id scene) :target-scene)))))))

(deftest test-execute-command-set-background
  (testing "Выполнение команды смены фона"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn]
        (script/add-scene!
          {:id :bg-test-scene
           :name "Тестовая сцена для фона"
           :background "/old-bg.jpg"
           :lines [{:text "Текст" :speaker nil}]})
        
        (script/jump-to-scene! :bg-test-scene)
        
        (script/execute-command
          {:type :set-background
           :image "/new-bg.jpg"})
        
        (let [db @conn
              current-scene-id (db/current-scene db)
              scene (d/entity db current-scene-id)]
          (is (= (:scene/background scene) "/new-bg.jpg")))))))

(deftest test-execute-command-unknown
  (testing "Выполнение неизвестной команды"
    (let [conn (create-test-environment)]
      (with-redefs [db/conn conn
                    println (fn [& args] nil)] ; заглушаем println
        ;; Не должно вызывать ошибку
        (script/execute-command
          {:type :unknown-command
           :data "some data"})))))

;; Функция для запуска всех тестов
(defn run-tests []
  (cljs.test/run-tests))