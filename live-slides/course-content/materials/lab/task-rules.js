/**
 * 判斷任務依賴與下一步排序的最小規則。
 * 本檔刻意保持純函式，方便先寫測試、再逐步加入實作。
 */

/**
 * 回傳指定任務目前是否可以完成。
 * @param {object} task 要判斷的任務
 * @param {object[]} tasks 完整任務清單
 * @returns {boolean} 是否允許完成
 */
export function canCompleteTask(task, tasks) {
  if (task.status === 'DONE') return false;
  if (task.dependsOnTaskId === null || task.dependsOnTaskId === undefined) return true;

  const dependency = tasks.find((item) => item.id === task.dependsOnTaskId);
  if (!dependency) {
    throw new Error(`找不到任務 ${task.dependsOnTaskId} 的前置任務`);
  }
  return dependency.status === 'DONE';
}

/**
 * 依規則計算畫面要顯示的狀態。
 * @param {object} task 要轉換的任務
 * @param {object[]} tasks 完整任務清單
 * @returns {object} 含衍生狀態的任務資料
 */
export function getTaskView(task, tasks) {
  const blocked = task.status !== 'DONE' && !canCompleteTask(task, tasks);
  return {
    ...task,
    displayStatus: blocked ? 'BLOCKED' : task.status,
    canComplete: task.status !== 'DONE' && !blocked,
  };
}

/**
 * 完成一個任務並回傳新的清單，不直接修改原始資料。
 * @param {object[]} tasks 原始任務清單
 * @param {number} taskId 要完成的任務 id
 * @returns {object[]} 更新後的任務清單
 */
export function completeTask(tasks, taskId) {
  const target = tasks.find((item) => item.id === taskId);
  if (!target) throw new Error(`找不到任務 ${taskId}`);
  if (!canCompleteTask(target, tasks)) throw new Error(`任務 ${taskId} 目前被阻塞`);
  return tasks.map((item) => (item.id === taskId ? { ...item, status: 'DONE' } : item));
}

/**
 * 找出目前最適合推薦的可執行任務。
 * @param {object[]} tasks 完整任務清單
 * @returns {object|null} 下一個任務，沒有則回傳 null
 */
export function getNextTask(tasks) {
  return tasks
    .map((task) => getTaskView(task, tasks))
    .filter((task) => task.canComplete)
    .sort((left, right) => left.priority - right.priority
      || left.createdAt.localeCompare(right.createdAt)
      || left.id - right.id)[0] || null;
}

/**
 * 計算完成比例，避免畫面自行重複實作數學規則。
 * @param {object[]} tasks 完整任務清單
 * @returns {number} 0 到 1 之間的比例
 */
export function completionRatio(tasks) {
  if (tasks.length === 0) return 0;
  return tasks.filter((task) => task.status === 'DONE').length / tasks.length;
}
