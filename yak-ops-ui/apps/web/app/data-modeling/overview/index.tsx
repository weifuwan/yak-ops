const EMPTY_IMAGE =
  "https://img.alicdn.com/imgextra/i4/O1CN017NgExC1MdM1GSlL3J_!!6000000001457-2-tps-720-720.png";

type StatisticValue = {
  num: string;
  unit: string;
};

type StatisticItem = {
  title: string;
  image?: string;
  values?: StatisticValue[];
  total?: boolean;
  formula?: string;
};

const STATISTICS: StatisticItem[] = [
  {
    title: "维度表模型",
    image:
      "https://img.alicdn.com/imgextra/i3/O1CN01bYykXi1ZHuHGBhaDL_!!6000000003170-2-tps-228-228.png",
    values: [{ num: "0", unit: "模型数量" }],
  },
  {
    title: "明细表模型",
    image:
      "https://img.alicdn.com/imgextra/i2/O1CN01l2o2g71MLUN0kZXpq_!!6000000001418-2-tps-228-228.png",
    values: [{ num: "2", unit: "模型数量" }],
  },
  {
    title: "汇总表模型",
    image:
      "https://img.alicdn.com/imgextra/i3/O1CN01RHb33F1ySm4GYFQiu_!!6000000006578-2-tps-228-228.png",
    values: [{ num: "0", unit: "模型数量" }],
  },
  {
    title: "应用表模型",
    image:
      "https://img.alicdn.com/imgextra/i2/O1CN018Lu3ns1kR1wLd4FXZ_!!6000000004679-2-tps-228-228.png",
    values: [{ num: "0", unit: "模型数量" }],
  },
  {
    title: "派生指标&复合指标",
    image:
      "https://img.alicdn.com/imgextra/i4/O1CN016myXwp1W58NunKeT1_!!6000000002736-2-tps-228-228.png",
    values: [
      { num: "3", unit: "指标数量" },
      { num: "1", unit: "向上取整" },
    ],
  },
  {
    title: "合计",
    total: true,
    values: [{ num: "3", unit: "" }],
    formula: "对象数量：模型数量+向上取整(指标数量/10)",
  },
];

function InfoIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <path d="M12 8h.01" />
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

/**
 * 数据统计卡片
 */
function StatisticCard({ item }: { item: StatisticItem }) {
  if (item.total) {
    return (
      <div className="mb-4 block w-full rounded-[2px] border border-[#EDEFF7] bg-white p-1.5 transition-shadow hover:shadow-[0_1px_8px_-1px_#0000001f]">
        <div className="flex h-6 flex-row">
          <div className="min-w-0 flex-1 overflow-hidden pl-1.5 text-ellipsis text-xs font-black leading-6 text-[#333946]">
            {item.title}
          </div>

          <div className="min-w-[80px] max-w-[100px] flex-1 overflow-hidden text-xs font-black leading-6 text-[#303642]">
            {item.values?.[0]?.num}
          </div>
        </div>

        <div className="overflow-hidden whitespace-nowrap pl-1.5 text-ellipsis text-xs leading-6 text-[#9299A5]">
          {item.formula}
        </div>
      </div>
    );
  }

  return (
    <div className="mb-4 flex w-full flex-row items-center rounded-[2px] border border-[#EDEFF7] bg-white p-1.5 transition-shadow hover:shadow-[0_1px_8px_-1px_#0000001f]">
      <img
        src={item.image}
        alt=""
        className="h-12 w-12 shrink-0 object-contain"
      />

      <div
        className="h-12 min-w-0 flex-1 overflow-hidden whitespace-nowrap px-1.5 text-ellipsis text-xs font-black leading-[48px] text-[#333946]"
        title={item.title}
      >
        {item.title}
      </div>

      {item.values?.map((value, index) => (
        <div
          key={index}
          className="h-12 min-w-[80px] max-w-[100px] flex-1 overflow-hidden"
        >
          <div className="h-6 overflow-hidden whitespace-nowrap text-ellipsis text-xs font-black leading-6 text-[#303642]">
            {value.num}
          </div>

          <div className="h-6 overflow-hidden whitespace-nowrap text-ellipsis text-xs leading-6 text-[#9299A5]">
            {value.unit}
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * 数据概览
 */
function DataOverviewCard() {
  return (
    <div className="mb-4 w-full overflow-hidden rounded-[4px] border border-[#EDEFF7] bg-white p-4">
      {/* Header */}
      <div className="flex min-h-5 w-full flex-row items-center">
        <div className="flex h-5 flex-1 items-center gap-1 text-base leading-5 text-[#10121A]">
          数据概览
          <span className="cursor-help text-[#FE2C55]">
            <InfoIcon />
          </span>
        </div>

        <div className="flex h-5 shrink-0 items-center gap-1 leading-5">
          <button
            type="button"
            className="cursor-pointer border-0 bg-transparent p-0 text-xs text-[#FE2C55] hover:opacity-80"
          >
            查看数据详情
          </button>

          <span className="cursor-help text-[#FE2C55]">
            <InfoIcon />
          </span>
        </div>
      </div>

      {/* Content */}
      <div className="relative mt-2 w-full">
        {/* 工作空间筛选 */}
        <div className="mb-2 flex h-8 w-[300px] max-w-full items-center text-xs">
          <label
            htmlFor="overview-workspace"
            className="w-1/4 shrink-0 whitespace-nowrap text-[#555B68]"
          >
            工作空间：
          </label>

          <div className="relative h-8 w-3/4">
            <select
              id="overview-workspace"
              defaultValue="all"
              className="h-8 w-full cursor-pointer appearance-none rounded border border-[#E5E7EB] bg-white pl-2.5 pr-8 text-xs text-[#333] outline-none transition-colors hover:border-[#B8BDC8] focus:border-[#FE2C55]"
            >
              <option value="all">全部</option>
            </select>

            <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-[#777E8B]">
              <ChevronDownIcon />
            </span>
          </div>
        </div>

        {/* 统计卡片 */}
        <div className="grid grid-cols-2 gap-x-4 min-[1500px]:grid-cols-3">
          {STATISTICS.map((item) => (
            <StatisticCard key={item.title} item={item} />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * 最新发布
 */
function LatestPublishCard() {
  return (
    <div className="mb-4 w-full overflow-hidden rounded-[4px] border border-[#EDEFF7] bg-white p-4">
      {/* Header */}
      <div className="flex min-h-5 w-full items-center">
        <div className="flex h-5 flex-1 items-center gap-1 text-base leading-5 text-[#10121A]">
          最新发布
          <span className="cursor-help text-[#FE2C55]">
            <InfoIcon />
          </span>
        </div>
      </div>

      {/* Empty State */}
      <div className="relative mt-2 w-full">
        <div className="flex h-[250px] w-full flex-col items-center justify-center overflow-hidden">
          <div
            className="h-[240px] w-[320px] max-w-full shrink rounded-none bg-contain bg-center bg-no-repeat"
            style={{
              backgroundImage: "url(" + EMPTY_IMAGE + ")",
            }}
          />

          <div className="mt-2 shrink-0 px-6 text-center text-xs leading-6 text-[#777E8B]">
            暂无数据
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 数据建模首页
 */
export default function DataModelingOverviewPage() {
  return (
    <main className="flex min-h-[calc(100vh-38px)] w-full flex-col overflow-x-auto bg-white text-[#10121A]">
      {/* 上半部分：主视觉 */}
      <section className="h-[46vh] min-h-[360px] max-h-[468px] w-full shrink-0 border-b border-[#EDF0F4] bg-[#F3F7FB]" />

      {/* 下半部分：数据概览 */}
      <section className="min-h-[420px] w-full flex-1 bg-white pb-4 pt-6">
        <div className="mx-auto w-[calc(100%_-_160px)] min-w-[1200px] max-w-[1600px]">
          {/* 24 栏响应式布局 */}
          <div className="-mx-2 grid grid-cols-[repeat(24,minmax(0,1fr))]">
            {/* 左侧：数据概览 */}
            <div className="col-span-[24] px-2 min-[1200px]:col-span-[17] min-[1500px]:col-span-[16]">
              <DataOverviewCard />
            </div>

            {/* 右侧：最新发布 */}
            <div className="col-span-[24] px-2 min-[1200px]:col-span-[7] min-[1500px]:col-span-[8]">
              <LatestPublishCard />
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
