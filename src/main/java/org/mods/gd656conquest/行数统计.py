import os
from pathlib import Path

def main():
    # 当前工作目录
    base_dir = Path.cwd()
    java_files = list(base_dir.rglob("*.java"))

    if not java_files:
        print("未找到任何 .java 文件。")
        return

    file_lines = []  # (文件路径, 行数)

    for file_path in java_files:
        try:
            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                lines = len(f.readlines())
            file_lines.append((file_path, lines))
        except Exception as e:
            print(f"警告：无法读取文件 {file_path}，错误：{e}")

    if not file_lines:
        print("没有成功读取任何 .java 文件。")
        return

    # 计算统计数据
    total_lines = sum(lines for _, lines in file_lines)
    file_count = len(file_lines)
    avg_lines = total_lines / file_count

    # 按行数降序排序
    sorted_files = sorted(file_lines, key=lambda x: x[1], reverse=True)

    max_lines = sorted_files[0][1]
    min_lines = sorted_files[-1][1]

    # 所有最大行文件
    max_files = [f for f, l in file_lines if l == max_lines]
    # 所有最小行文件
    min_files = [f for f, l in file_lines if l == min_lines]

    print(f"统计目录: {base_dir}")
    print(f"总共找到 .java 文件数: {file_count}")
    print(f"平均行数: {avg_lines:.2f}")

    print(f"\n最大行文件 (行数: {max_lines}):")
    for f in max_files:
        print(f"  {f}")

    print(f"\n最小行文件 (行数: {min_lines}):")
    for f in min_files:
        print(f"  {f}")

    # 行数排名前10（取前10个，若不足10个则全部显示）
    top_n = min(10, len(sorted_files))
    print(f"\n行数排名前{top_n}:")
    for i, (f, l) in enumerate(sorted_files[:top_n], 1):
        print(f"{i:2d}. {f} - {l} 行")

if __name__ == "__main__":
    main()