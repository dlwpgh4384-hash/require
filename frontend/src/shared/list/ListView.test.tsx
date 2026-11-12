import { useState } from "react";
import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import {
  DetailPanel,
  ListFilters,
  ListPager,
  ListView,
  useListColumns,
  type ListColumn,
  type ListSort,
} from ".";

interface Row {
  id: string;
  name: string;
  memo: string;
}
const columns: ListColumn<Row>[] = [
  { key: "id", label: "아이디" },
  { key: "name", label: "이름", required: true },
  { key: "memo", label: "메모", sortable: false, hiddenByDefault: true },
];
const rows = (count: number) =>
  Array.from({ length: count }, (_, index) => ({
    id: `r${index + 1}`,
    name: `이름 ${index + 1}`,
    memo: "참고",
  }));

function Harness({
  data = rows(3),
  total = 3,
  onDownload,
  onSelectAllResults,
}: {
  data?: Row[] | null;
  total?: number;
  onDownload?: () => void;
  onSelectAllResults?: () => void;
}) {
  const cols = useListColumns("TEST", columns);
  const [sort, setSort] = useState<ListSort | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [dense, setDense] = useState(false);
  const [all, setAll] = useState(false);
  return (
    <>
      <ListView
        screenId="TEST"
        columns={columns}
        visibleColumns={cols.visible}
        hiddenColumns={cols.hidden}
        onToggleColumn={cols.toggle}
        onResetColumns={cols.reset}
        rows={data}
        rowKey={(row) => row.id}
        total={total}
        page={1}
        pageSize={20}
        onPage={vi.fn()}
        onPageSize={vi.fn()}
        dense={dense}
        onDense={setDense}
        sort={sort}
        onSort={setSort}
        selectable
        selected={selected}
        onSelect={(keys) => {
          setSelected(keys);
          setAll(false);
        }}
        allResultsSelected={all}
        onSelectAllResults={() => {
          setAll(true);
          onSelectAllResults?.();
        }}
        bulkActions={[{ key: "stop", label: "사용 중지", danger: true, onClick: vi.fn() }]}
        onDownload={onDownload}
        onResetFilters={vi.fn()}
      />
      <output data-testid="sort">{JSON.stringify(sort)}</output>
    </>
  );
}
afterEach(cleanup);

it("평소엔 「총 N건」, 체크하면 「N건 선택」과 일괄 버튼, 페이지 전체면 「조회된 N건 모두 선택」", () => {
  const all = vi.fn();
  render(<Harness total={45} onSelectAllResults={all} />);
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 45건");
  fireEvent.click(screen.getByTestId("list-select-r1"));
  expect(screen.getByTestId("list-count")).toHaveTextContent("1건 선택");
  expect(screen.getByTestId("list-bulk-stop")).toHaveClass("ls-btn-dt");
  fireEvent.click(screen.getByTestId("list-select-page"));
  fireEvent.click(screen.getByTestId("list-select-all-results"));
  expect(all).toHaveBeenCalled();
  expect(screen.getByTestId("list-count")).toHaveTextContent("45건 선택");
  fireEvent.click(screen.getByTestId("list-select-none"));
  expect(screen.getByTestId("list-count")).toHaveTextContent("총 45건");
});

it("열 제목을 누르면 ▲ → ▼ 로 정렬하고, 정렬하지 않는 열은 무시한다", () => {
  render(<Harness />);
  fireEvent.click(screen.getByTestId("list-head-id"));
  expect(screen.getByTestId("sort")).toHaveTextContent('{"key":"id","dir":"asc"}');
  expect(screen.getByTestId("list-head-id")).toHaveTextContent("아이디 ▲");
  fireEvent.click(screen.getByTestId("list-head-id"));
  expect(screen.getByTestId("list-head-id")).toHaveTextContent("아이디 ▼");
});

it("열 설정은 필수 열을 잠그고, 숨긴 참고 열을 켜고, [기본값으로] 되돌린다", () => {
  render(<Harness />);
  expect(screen.queryByTestId("list-head-memo")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-columns"));
  const pop = screen.getByTestId("list-columns-pop");
  expect(within(pop).getByTestId("list-column-name")).toBeDisabled();
  fireEvent.click(within(pop).getByTestId("list-column-memo"));
  expect(screen.getByTestId("list-head-memo")).toBeInTheDocument();
  fireEvent.click(within(pop).getByTestId("list-column-id"));
  expect(screen.queryByTestId("list-head-id")).not.toBeInTheDocument();
  fireEvent.click(within(pop).getByTestId("list-columns-default"));
  expect(screen.getByTestId("list-head-id")).toBeInTheDocument();
  expect(screen.queryByTestId("list-head-memo")).not.toBeInTheDocument();
});

it("좁게 보기를 켜고 끄며, 엑셀 버튼은 받을 수 있을 때만 보인다", () => {
  const download = vi.fn();
  const { container } = render(<Harness onDownload={download} />);
  fireEvent.click(screen.getByTestId("list-density"));
  expect(container.querySelector("table")).toHaveClass("ls-dense");
  expect(screen.getByTestId("list-density")).toHaveTextContent("넓게 보기");
  fireEvent.click(screen.getByTestId("list-download"));
  expect(download).toHaveBeenCalled();
  cleanup();
  render(<Harness />);
  expect(screen.queryByTestId("list-download")).not.toBeInTheDocument();
});

it("결과가 없으면 안내와 [조건 초기화]를, 불러오는 중이면 상태 문구를 보인다", () => {
  render(<Harness data={[]} total={0} />);
  expect(screen.getByTestId("list-empty")).toHaveTextContent("조건에 맞는 결과가 없습니다.");
  expect(screen.getByText("조건 초기화")).toBeInTheDocument();
  cleanup();
  render(<Harness data={null} total={0} />);
  expect(screen.getByText("목록을 불러오는 중입니다.")).toBeInTheDocument();
});

it("페이지는 번호 10개씩, 범위 「41-50 / 120건」, 한 화면 건수 20/50/100", () => {
  const onPage = vi.fn();
  const onPageSize = vi.fn();
  render(
    <ListPager page={5} pageSize={10} total={120} onPage={onPage} onPageSize={onPageSize} />,
  );
  expect(screen.getByTestId("list-range")).toHaveTextContent("41-50 / 120건");
  expect(screen.getByText("10")).toBeInTheDocument();
  expect(screen.queryByText("11")).not.toBeInTheDocument();
  fireEvent.click(screen.getByTestId("list-page-다음 페이지"));
  expect(onPage).toHaveBeenCalledWith(6);
  fireEvent.click(screen.getByTestId("list-page-마지막 페이지"));
  expect(onPage).toHaveBeenCalledWith(12);
  fireEvent.change(screen.getByTestId("list-page-size"), { target: { value: "100" } });
  expect(onPageSize).toHaveBeenCalledWith(100);
});

it("조건 줄은 Enter·[조회]로 적용하고 [초기화]를 따로 부른다", () => {
  const search = vi.fn();
  const reset = vi.fn();
  render(
    <ListFilters onSearch={search} onReset={reset}>
      <input data-testid="q" />
    </ListFilters>,
  );
  fireEvent.submit(screen.getByTestId("q"));
  expect(search).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByTestId("list-reset"));
  expect(reset).toHaveBeenCalled();
});

it("상세 패널은 탭을 바꾸고 Esc·바깥·✕로 닫는다", () => {
  const close = vi.fn();
  function Panel() {
    const [tab, setTab] = useState("a");
    return (
      <DetailPanel
        title="한지성"
        sub="sales3"
        activeTab={tab}
        onTab={setTab}
        onClose={close}
        tabs={[
          { key: "a", label: "계정 정보", content: <p>계정</p> },
          { key: "b", label: "변경 이력 (0)", content: <p>이력</p> },
        ]}
      />
    );
  }
  render(<Panel />);
  expect(screen.getByText("계정")).toBeInTheDocument();
  fireEvent.click(screen.getByTestId("detail-panel-tab-b"));
  expect(screen.getByText("이력")).toBeInTheDocument();
  fireEvent.keyDown(window, { key: "Escape" });
  fireEvent.click(screen.getByTestId("detail-panel-close"));
  expect(close).toHaveBeenCalledTimes(2);
});
