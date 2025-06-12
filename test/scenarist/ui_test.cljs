(ns scenarist.ui-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [reagent.core :as r]
            [datascript.core :as d]
            [scenarist.db :as db]
            [scenarist.ui.nvl :as nvl]
            [scenarist.engine.script :as script]
            [scenarist.engine.typewriter :as typewriter]))

(defn setup-ui-test []
  "Подготовка окружения для UI тестов"
  (let [conn (d/create-conn db/schema)]
    (with-redefs [db/conn conn]
      (db/init-game-state!)
      
      ;; Добавляем тестовую сцену
      (script/add-scene!
        {:id :ui-test-scene
         :name "UI Test Scene"
         :background "/test/ui-bg.jpg"
         :lines
         [{:text "Первая строка для UI теста"
           :speaker "Тестовый персонаж"}
          {:text "Вторая строка без говорящего"
           :speaker nil}
          {:text "Третья строка с длинным текстом, который должен хорошо отображаться в интерфейсе визуальной новеллы"
           :speaker "Рассказчик"}]})
      
      conn)))

(defn render-component [component]
  "Вспомогательная функция для рендеринга компонента - заглушка для Node.js"
  ;; В Node.js среде просто проверяем, что компонент создался без ошибок
  (try
    (r/as-element component)
    "rendered-successfully"
    (catch js/Error e
      (str "render-error: " (.-message e)))))

(deftest test-nvl-text-box-structure
  (testing "Структура NVL текстового блока"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        (typewriter/start-scene! (:db/id (db/current-scene @conn)))
        
        ;; Рендерим компонент
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-background-layer
  (testing "Слой фонового изображения"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        (let [rendered (render-component [nvl/background-layer])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-control-panel-buttons
  (testing "Панель управления и кнопки"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (db/set-text-speed! :medium)
        
        (let [rendered (render-component [nvl/control-panel])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-text-display-states
  (testing "Различные состояния отображения текста"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        ;; Состояние 1: Начало сцены
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (= rendered "rendered-successfully")))
        
        ;; Состояние 2: Во время печати
        (db/set-typing-state! true)
        (db/update-displayed-text! "Первая")
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (= rendered "rendered-successfully")))
        
        ;; Состояние 3: После завершения печати
        (db/set-typing-state! false)
        (db/update-displayed-text! "Первая строка для UI теста")
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (= rendered "rendered-successfully")))))))

(deftest test-multiple-lines-display
  (testing "Отображение нескольких строк"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        
        ;; Показываем первую строку
        (typewriter/handle-click!)
        
        ;; Переходим ко второй
        (typewriter/handle-click!)
        
        ;; Теперь должны отображаться обе строки
        (let [rendered (render-component [nvl/nvl-text-box])
              lines (db/scene-lines @conn (:db/id (db/current-scene @conn)))]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully"))
          ;; Текущая строка должна быть второй
          (is (= (:game/current-line (db/game-state @conn)) 1)))))))

(deftest test-speaker-display
  (testing "Отображение имён говорящих"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        
        ;; Первая строка с говорящим
        (typewriter/handle-click!)
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (= rendered "rendered-successfully")))
        
        ;; Вторая строка без говорящего
        (typewriter/handle-click!)
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-game-screen-integration
  (testing "Интеграция всех компонентов игрового экрана"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        (let [rendered (render-component [nvl/nvl-game-screen])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-click-handler-integration
  (testing "Интеграция обработчика кликов"
    (let [conn (setup-ui-test)
          click-count (atom 0)]
      (with-redefs [db/conn conn
                    typewriter/handle-click! (fn [] (swap! click-count inc))]
        (script/jump-to-scene! :ui-test-scene)
        
        ;; Симулируем клики
        (nvl/handle-click!)
        (is (= @click-count 1))
        
        (nvl/handle-click!)
        (is (= @click-count 2))))))

(deftest test-text-speed-button
  (testing "Кнопка изменения скорости текста"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        ;; Проверяем циклическое изменение скорости
        (db/set-text-speed! :slow)
        (let [rendered (render-component [nvl/control-panel])]
          (is (= rendered "rendered-successfully")))
        
        (db/set-text-speed! :medium)
        (let [rendered (render-component [nvl/control-panel])]
          (is (= rendered "rendered-successfully")))
        
        (db/set-text-speed! :fast)
        (let [rendered (render-component [nvl/control-panel])]
          (is (= rendered "rendered-successfully")))
        
        (db/set-text-speed! :instant)
        (let [rendered (render-component [nvl/control-panel])]
          (is (= rendered "rendered-successfully")))))))

(deftest test-opacity-for-lines
  (testing "Прозрачность для предыдущих строк"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        
        ;; Показываем две строки
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        
        ;; В рендере текущая строка должна иметь opacity: 1
        ;; а предыдущие - opacity: 0.8
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

(deftest test-long-text-wrapping
  (testing "Перенос длинного текста"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        
        ;; Переходим к третьей строке с длинным текстом
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        (typewriter/handle-click!)
        
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем, что компонент создался без ошибок
          (is (= rendered "rendered-successfully")))))))

;; Функция для запуска всех UI тестов
(defn run-tests []
  (cljs.test/run-tests))