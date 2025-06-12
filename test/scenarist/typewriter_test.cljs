(ns scenarist.typewriter-test
  (:require [cljs.test :refer-macros [deftest testing is async]]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.engine.typewriter :as typewriter]))

(defn create-test-scenario []
  (let [conn (d/create-conn db/schema)]
    ;; Инициализация игрового состояния
    (with-redefs [db/conn conn]
      (db/init-game-state!)
      
      ;; Добавляем тестовые данные
      (d/transact! conn
        [{:db/id -1
          :line/id :test-line1
          :line/text "Первая строка для тестирования typewriter"
          :line/speaker "Тестер"
          :line/position 0}
         {:db/id -2
          :line/id :test-line2
          :line/text "Вторая строка диалога"
          :line/speaker "Тестер"
          :line/position 1}
         {:db/id -3
          :scene/id :test-scene
          :scene/name "Тестовая сцена"
          :scene/background "/test-bg.jpg"
          :scene/lines [-1 -2]}])
      
      (db/set-current-scene! :test-scene)
      conn)))

(deftest test-speed-map
  (testing "Проверка карты скоростей"
    (is (= (:slow typewriter/speed-map) 100))
    (is (= (:medium typewriter/speed-map) 50))
    (is (= (:fast typewriter/speed-map) 20))
    (is (= (:instant typewriter/speed-map) 0))))

(deftest test-instant-typing
  (testing "Мгновенное отображение текста"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        (db/set-text-speed! :instant)
        (typewriter/start-typing! "Тестовый текст")
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/displayed-text state) "Тестовый текст"))
          (is (= (:game/is-typing state) false)))))))

(deftest test-skip-typing
  (testing "Пропуск анимации печати"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        ;; Устанавливаем состояние печати
        (db/set-typing-state! true)
        (db/update-displayed-text! "Частичный")
        
        ;; Пропускаем
        (typewriter/skip-typing!)
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/displayed-text state) "Первая строка для тестирования typewriter"))
          (is (= (:game/is-typing state) false)))))))

(deftest test-advance-line
  (testing "Переход к следующей строке"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        ;; Начинаем с первой строки
        (is (= (:game/current-line (db/game-state @conn)) 0))
        
        ;; Переходим к следующей
        (typewriter/advance-line!)
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/current-line state) 1)))))))

(deftest test-handle-click-during-typing
  (testing "Клик во время печати должен пропустить анимацию"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        ;; Устанавливаем состояние печати
        (db/set-typing-state! true)
        (db/update-displayed-text! "Част")
        
        ;; Кликаем
        (typewriter/handle-click!)
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/displayed-text state) "Первая строка для тестирования typewriter"))
          (is (= (:game/is-typing state) false)))))))

(deftest test-handle-click-after-typing
  (testing "Клик после завершения печати должен перейти к следующей строке"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        ;; Завершаем печать первой строки
        (db/set-typing-state! false)
        (db/update-displayed-text! "Первая строка для тестирования typewriter")
        
        ;; Кликаем
        (typewriter/handle-click!)
        
        (let [db @conn
              state (db/game-state db)]
          (is (= (:game/current-line state) 1)))))))

(deftest test-start-scene
  (testing "Запуск сцены должен начать печать первой строки"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        (let [scene-id (:db/id (db/current-scene @conn))]
          ;; Устанавливаем мгновенную скорость перед запуском
          (db/set-text-speed! :instant)
          (typewriter/start-scene! scene-id)
          
          (let [db @conn
                state (db/game-state db)]
            ;; При мгновенной скорости текст должен появиться сразу
            (is (= (:game/displayed-text state) "Первая строка для тестирования typewriter"))))))))

(deftest test-clear-typing
  (testing "Очистка таймаута печати"
    ;; Устанавливаем фейковый таймаут
    (reset! typewriter/typing-timeout 123)
    
    ;; Очищаем
    (typewriter/clear-typing!)
    
    ;; Проверяем, что таймаут сброшен
    (is (nil? @typewriter/typing-timeout))))

(deftest test-end-of-scene
  (testing "Поведение при достижении конца сцены"
    (let [conn (create-test-scenario)]
      (with-redefs [db/conn conn]
        ;; Переходим к последней строке
        (db/advance-line!)
        (is (= (:game/current-line (db/game-state @conn)) 1))
        
        ;; Пытаемся перейти дальше
        (with-redefs [println (fn [& args] nil)] ; заглушаем println
          (typewriter/advance-line!))
        
        ;; Должны остаться на той же строке
        (is (= (:game/current-line (db/game-state @conn)) 1))))))

;; Функция для запуска всех тестов
(defn run-tests []
  (cljs.test/run-tests))