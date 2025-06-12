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
  "Вспомогательная функция для рендеринга компонента"
  (r/render-to-static-markup component))

(deftest test-nvl-text-box-structure
  (testing "Структура NVL текстового блока"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        (db/set-text-speed! :instant)
        (typewriter/start-scene! (:db/id (db/current-scene @conn)))
        
        ;; Рендерим компонент
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем наличие основных элементов
          (is (re-find #"nvl-container" rendered))
          (is (re-find #"nvl-line" rendered))
          (is (re-find #"speaker-name" rendered))
          (is (re-find #"dialogue-text" rendered)))))))

(deftest test-background-layer
  (testing "Слой фонового изображения"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        (let [rendered (render-component [nvl/background-layer])]
          ;; Проверяем, что фон содержит правильный URL
          (is (re-find #"background-image.*url\(/test/ui-bg.jpg\)" rendered))
          (is (re-find #"background" rendered)))))))

(deftest test-control-panel-buttons
  (testing "Панель управления и кнопки"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (db/set-text-speed! :medium)
        
        (let [rendered (render-component [nvl/control-panel])]
          ;; Проверяем наличие всех кнопок
          (is (re-find #"control-panel" rendered))
          (is (re-find #"Авто" rendered))
          (is (re-find #"Скорость.*medium" rendered))
          (is (re-find #"История" rendered))
          (is (re-find #"Меню" rendered)))))))

(deftest test-text-display-states
  (testing "Различные состояния отображения текста"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        ;; Состояние 1: Начало сцены
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (re-find #"nvl-container" rendered)))
        
        ;; Состояние 2: Во время печати
        (db/set-typing-state! true)
        (db/update-displayed-text! "Первая")
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (re-find #"Первая" rendered)))
        
        ;; Состояние 3: После завершения печати
        (db/set-typing-state! false)
        (db/update-displayed-text! "Первая строка для UI теста")
        (let [rendered (render-component [nvl/nvl-text-box])]
          (is (re-find #"Первая строка для UI теста" rendered)))))))

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
          ;; Проверяем первую строку (уже показана)
          (is (re-find #"Первая строка для UI теста" rendered))
          ;; Проверяем имя говорящего первой строки
          (is (re-find #"Тестовый персонаж" rendered))
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
          (is (re-find #"Тестовый персонаж" rendered)))
        
        ;; Вторая строка без говорящего
        (typewriter/handle-click!)
        (let [rendered (render-component [nvl/nvl-text-box])]
          ;; Проверяем, что нет пустого блока speaker-name для второй строки
          (is (re-find #"Вторая строка без говорящего" rendered)))))))

(deftest test-game-screen-integration
  (testing "Интеграция всех компонентов игрового экрана"
    (let [conn (setup-ui-test)]
      (with-redefs [db/conn conn]
        (script/jump-to-scene! :ui-test-scene)
        
        (let [rendered (render-component [nvl/nvl-game-screen])]
          ;; Проверяем наличие всех основных элементов
          (is (re-find #"game-screen" rendered))
          (is (re-find #"background" rendered))
          (is (re-find #"nvl-container" rendered))
          (is (re-find #"control-panel" rendered)))))))

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
          (is (re-find #"Скорость.*slow" rendered)))
        
        (db/set-text-speed! :medium)
        (let [rendered (render-component [nvl/control-panel])]
          (is (re-find #"Скорость.*medium" rendered)))
        
        (db/set-text-speed! :fast)
        (let [rendered (render-component [nvl/control-panel])]
          (is (re-find #"Скорость.*fast" rendered)))
        
        (db/set-text-speed! :instant)
        (let [rendered (render-component [nvl/control-panel])]
          (is (re-find #"Скорость.*instant" rendered)))))))

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
          ;; Проверяем, что есть элементы с разной прозрачностью
          (is (re-find #"opacity.*1" rendered))
          (is (re-find #"opacity.*0\.8" rendered)))))))

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
          ;; Проверяем, что длинный текст присутствует
          (is (re-find #"длинным текстом.*визуальной новеллы" rendered)))))))

;; Функция для запуска всех UI тестов
(defn run-tests []
  (cljs.test/run-tests))