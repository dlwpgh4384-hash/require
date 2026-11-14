import { useEffect, useRef, useState, type ReactNode } from "react";
import { Dialog } from "../../shared/feedback/Dialog";
import { useScreenDirty } from "../../shared/layout/ScreenTabs";
import { useScreenTitleOverride } from "../../shared/layout/ScreenTitleOverride";
import {
  DeveloperRequestError,
  developerFailure,
  getCommonDeveloperAreas,
  postCommonDeveloper,
  putCommonDeveloper,
  type Developer,
  type DeveloperArea,
  type DeveloperInput,
  type DeveloperOptions,
} from "./developers";
import "./developers.css";

export function DeveloperForm({
  developer,
  options,
  onCancel,
  onSaved,
}: {
  developer?: Developer;
  options: DeveloperOptions;
  onCancel: () => void;
  onSaved: (developer: Developer) => void;
}) {
  const [draft, setDraft] = useState<DeveloperInput>(() => ({
    name: developer?.name || "",
    managementNumber: developer?.managementNumber || "",
    employeeTypeCode: developer?.employeeTypeCode || "",
    organizationId: developer?.organizationId ?? "",
    gradeCode: developer?.gradeCode || "",
    primaryJobCode: developer?.primaryJobCode || "",
    secondaryJobCode: developer?.secondaryJobCode || "",
    positionCode:
      developer?.positionCode ||
      options.codes.JBPS?.find((c) => c.codeName === "팀원")?.code ||
      "",
    directClassCode:
      developer?.directClassCode ||
      options.codes.DRCT_CLS?.find((c) => c.codeName === "직접")?.code ||
      "",
    salesPartId: developer?.salesPartId || "",
    hireDate: developer?.hireDate || "",
    monthlyRate: developer?.monthlyRate ?? null,
    monthlyPay: developer?.monthlyPay ?? null,
    skillCodes: developer?.skillCodes || [],
    homeAreaCode: developer?.homeAreaCode || null,
  }));
  const original = useRef(JSON.stringify(draft));
  const dirty = original.current !== JSON.stringify(draft);
  const setDirty = useScreenDirty();
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [areas, setAreas] = useState<DeveloperArea[][]>([[], [], []]);
  const [location, setLocation] = useState(["", "", ""]);
  const [areaBusy, setAreaBusy] = useState(true);
  const [areaError, setAreaError] = useState("");
  const [areaRevision, setAreaRevision] = useState(0);
  const areaRequest = useRef(0);
  useEffect(() => {
    setDirty("UI-02", dirty);
    return () => setDirty("UI-02", false);
  }, [dirty, setDirty]);
  const leave = () => (dirty ? setLeaving(true) : onCancel());
  useScreenTitleOverride(
    "UI-02",
    developer ? "개발자 등록 수정" : "개발자 등록",
    leave,
  );
  useEffect(() => {
    let active = true;
    const load = async () => {
      setAreaBusy(true);
      setAreaError("");
      try {
        const root = (await getCommonDeveloperAreas()).items;
        let lists = [root, [] as DeveloperArea[], [] as DeveloperArea[]],
          selected = ["", "", ""];
        const code = draft.homeAreaCode;
        if (code) {
          selected[0] =
            root.find((a) => a.areaCode.slice(0, 2) === code.slice(0, 2))
              ?.areaCode || "";
          if (selected[0] && selected[0] !== code) {
            lists[1] = (await getCommonDeveloperAreas(selected[0])).items;
            selected[1] =
              lists[1].find((a) => a.areaCode.slice(0, 5) === code.slice(0, 5))
                ?.areaCode || "";
            if (selected[1] && selected[1] !== code) {
              lists[2] = (await getCommonDeveloperAreas(selected[1])).items;
              selected[2] =
                lists[2].find((a) => a.areaCode === code)?.areaCode || "";
            }
          }
        }
        if (active) {
          setAreas(lists);
          setLocation(selected);
        }
      } catch (reason) {
        if (active) setAreaError(developerFailure(reason));
      } finally {
        if (active) setAreaBusy(false);
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [developer?.homeAreaCode, areaRevision]);
  function change<K extends keyof DeveloperInput>(
    key: K,
    value: DeveloperInput[K],
  ) {
    setDraft((d) => ({ ...d, [key]: value }));
  }
  const selectArea = async (level: number, value: string) => {
    const request = ++areaRequest.current;
    const next = location.map((v, i) =>
      i === level ? value : i > level ? "" : v,
    );
    setLocation(next);
    change("homeAreaCode", next[2] || next[1] || next[0] || null);
    setAreas((old) => old.map((list, i) => (i > level ? [] : list)));
    setAreaError("");
    if (!value || level === 2) {
      setAreaBusy(false);
      return;
    }
    setAreaBusy(true);
    try {
      const response = await getCommonDeveloperAreas(value);
      if (request === areaRequest.current)
        setAreas((old) =>
          old.map((list, i) => (i === level + 1 ? response.items : list)),
        );
    } catch (reason) {
      if (request === areaRequest.current)
        setAreaError(developerFailure(reason));
    } finally {
      if (request === areaRequest.current) setAreaBusy(false);
    }
  };
  const fieldError = (key: string) =>
    error instanceof DeveloperRequestError
      ? error.errors.find((e) => e.field === key)?.message
      : undefined;
  const field = (key: string, label: string, control: ReactNode) => (
    <div className="developer-field" key={key}>
      <label htmlFor={`developer-${key}`}>{label}</label>
      {control}
      {fieldError(key) && (
        <span role="alert" className="field-error">
          {fieldError(key)}
        </span>
      )}
    </div>
  );
  const codeSelect = (
    key: keyof DeveloperInput,
    label: string,
    group: string,
    required = false,
  ) =>
    field(
      key,
      label,
      <select
        id={`developer-${key}`}
        data-testid={`developer-${key}`}
        value={String(draft[key] || "")}
        disabled={busy}
        onChange={(e) => change(key, e.target.value)}
        required={required}
      >
        <option value="">선택</option>
        {!!draft[key] &&
          !options.codes[group]?.some((c) => c.code === draft[key]) && (
            <option value={String(draft[key])} disabled>
              {String(draft[key])} (기존값·새 선택 불가)
            </option>
          )}
        {options.codes[group]?.map((c) => (
          <option key={c.code} value={c.code}>
            {c.codeName}
          </option>
        ))}
      </select>,
    );
  const save = async () => {
    if (saving.current || areaBusy || areaError) return;
    saving.current = true;
    setBusy(true);
    setError(null);
    try {
      const saved = developer
        ? await putCommonDeveloper(developer.developerId, draft)
        : await postCommonDeveloper(draft);
      original.current = JSON.stringify(draft);
      setDirty("UI-02", false);
      onSaved(saved);
    } catch (reason) {
      setError(reason);
    } finally {
      saving.current = false;
      setBusy(false);
    }
  };
  return (
    <section
      className="developer-form"
      data-testid="developer-form"
      data-screen-id="UI-02"
    >
      <h2>{developer ? "개발자 등록 수정" : "개발자 등록"}</h2>
      <p className="ls-banner ls-banner-info">
        보유 스킬·거주지는 이 화면에서만 입력합니다(「내 정보」에서는 고치지
        않음). 거주지는 시·도, 시·군·구, 동까지 받습니다(번지는 받지 않음).
      </p>
      {error != null && <p role="alert">{developerFailure(error)}</p>}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
        noValidate
      >
        <div className="developer-fields">
          {field(
            "name",
            "이름 *",
            <input
              id="developer-name"
              data-testid="developer-name"
              value={draft.name}
              maxLength={50}
              disabled={busy}
              onChange={(e) => change("name", e.target.value)}
            />,
          )}
          {field(
            "managementNumber",
            "관리번호",
            <>
              <input
                id="developer-managementNumber"
                data-testid="developer-management-number"
                value={draft.managementNumber}
                readOnly={!!developer}
                className={developer ? "developer-locked" : ""}
                maxLength={20}
                disabled={busy}
                onChange={(e) => change("managementNumber", e.target.value)}
              />
              <small>
                {developer
                  ? "관리번호는 바꿀 수 없습니다. 잘못 넣었으면 사용 중지 후 새로 등록하세요."
                  : "비우면 K-nnnn 번호가 자동 발급됩니다."}
              </small>
            </>,
          )}
          {codeSelect("employeeTypeCode", "직원구분 *", "EMP_TYPE", true)}
          {field(
            "organizationId",
            "소속 *",
            <select
              id="developer-organizationId"
              data-testid="developer-organization"
              value={draft.organizationId}
              disabled={busy}
              onChange={(e) => change("organizationId", e.target.value)}
            >
              <option value="">자사</option>
              {options.organizations
                .filter(
                  (o) => o.useYn === "Y" || o.id === developer?.organizationId,
                )
                .map((o) => (
                  <option key={o.id} value={o.id} disabled={o.useYn !== "Y"}>
                    {o.name}
                    {o.useYn !== "Y" ? " (중지)" : ""}
                  </option>
                ))}
            </select>,
          )}
          {codeSelect("gradeCode", "등급", "GRADE")}
          {codeSelect("primaryJobCode", "주 직무 *", "JOB", true)}
          {codeSelect("secondaryJobCode", "보조 직무", "JOB")}
          {codeSelect("positionCode", "직위", "JBPS")}
          {codeSelect("directClassCode", "직접/간접 *", "DRCT_CLS", true)}
          {field(
            "salesPartId",
            "영업 파트 *",
            <select
              id="developer-salesPartId"
              data-testid="developer-sales-part"
              value={draft.salesPartId}
              disabled={busy}
              onChange={(e) => change("salesPartId", e.target.value)}
            >
              <option value="">선택</option>
              {options.parts
                .filter(
                  (o) => o.useYn === "Y" || o.id === developer?.salesPartId,
                )
                .map((o) => (
                  <option key={o.id} value={o.id} disabled={o.useYn !== "Y"}>
                    {o.name}
                    {o.useYn !== "Y" ? " (중지)" : ""}
                  </option>
                ))}
            </select>,
          )}
          {field(
            "hireDate",
            "입사일",
            <input
              id="developer-hireDate"
              data-testid="developer-hire-date"
              type="date"
              value={draft.hireDate || ""}
              disabled={busy}
              onChange={(e) => change("hireDate", e.target.value)}
            />,
          )}
          {(["monthlyRate", "monthlyPay"] as const).map((key) =>
            field(
              key,
              key === "monthlyRate" ? "월 단가" : "월 급여·지급액",
              <input
                id={`developer-${key}`}
                data-testid={`developer-${key}`}
                type="number"
                min="0"
                value={draft[key] ?? ""}
                disabled={busy}
                onChange={(e) =>
                  change(
                    key,
                    e.target.value === "" ? null : Number(e.target.value),
                  )
                }
              />,
            ),
          )}
        </div>
        <p>
          간접은 관리직만 선택하세요. 월 단가·급여 적용 시작월은 저장한
          달입니다.
        </p>
        <fieldset disabled={busy}>
          <legend>보유 스킬</legend>
          <div className="developer-skills">
            {options.codes.SKILL?.map((c) => (
              <label key={c.code}>
                <input
                  type="checkbox"
                  data-testid={`developer-skill-${c.code}`}
                  checked={draft.skillCodes?.includes(c.code) || false}
                  onChange={(e) =>
                    change(
                      "skillCodes",
                      e.target.checked
                        ? [...(draft.skillCodes || []), c.code]
                        : draft.skillCodes?.filter((v) => v !== c.code),
                    )
                  }
                />
                {c.codeName}
              </label>
            ))}
          </div>
          {draft.skillCodes
            ?.filter(
              (c) => !options.codes.SKILL?.some((item) => item.code === c),
            )
            .map((c) => (
              <label className="field-error" key={c}>
                <input
                  type="checkbox"
                  data-testid={`developer-legacy-skill-${c}`}
                  checked
                  onChange={() =>
                    change(
                      "skillCodes",
                      draft.skillCodes?.filter((v) => v !== c),
                    )
                  }
                />
                확인 필요: {c} (해제 후 현재 스킬을 선택하세요)
              </label>
            ))}
        </fieldset>
        <div className="developer-fields">
          {["시·도", "시·군·구", "동"].map((label, i) =>
            field(
              `area-${i}`,
              label,
              <select
                id={`developer-area-${i}`}
                data-testid={`developer-area-${i}`}
                value={location[i]}
                disabled={busy || areaBusy || (i > 0 && !location[i - 1])}
                onChange={(e) => void selectArea(i, e.target.value)}
              >
                <option value="">선택</option>
                {areas[i].map((a) => (
                  <option key={a.areaCode} value={a.areaCode}>
                    {a.areaName}
                  </option>
                ))}
              </select>,
            ),
          )}
        </div>
        {areaBusy && <p role="status">거주지 읽는 중…</p>}
        {areaError && (
          <p role="alert">
            {areaError}
            <button
              type="button"
              data-testid="developer-area-retry"
              onClick={() => setAreaRevision((v) => v + 1)}
            >
              재시도
            </button>
          </p>
        )}
        <footer className="developer-footer">
          <button
            type="button"
            data-testid="developer-form-cancel"
            disabled={busy}
            onClick={leave}
          >
            취소
          </button>
          <button
            type="submit"
            data-testid="developer-form-save"
            disabled={busy || areaBusy || !!areaError}
            className="primary-button"
          >
            {busy ? "저장 중…" : "저장"}
          </button>
        </footer>
      </form>
      {leaving && (
        <Dialog
          title="저장하지 않고 나갈까요?"
          onClose={() => setLeaving(false)}
        >
          <p>입력한 내용이 사라집니다.</p>
          <div className="app-actions">
            <button
              data-testid="developer-stay"
              onClick={() => setLeaving(false)}
            >
              계속 입력
            </button>
            <button data-testid="developer-discard" onClick={onCancel}>
              나가기
            </button>
          </div>
        </Dialog>
      )}
    </section>
  );
}
