"use client";

import Link from "next/link";
import { useLayoutEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { DESCRIPTION_MAX, TITLE_MAX, type BroadcastInput } from "@/lib/broadcast";
import { useAuth } from "../auth/AuthProvider";
import { ErrorMessage, PrimaryButton } from "../ui";

const EMPTY: BroadcastInput = { title: "", description: "" };

/** 서버와 같은 규칙을 먼저 확인해 왕복 없이 안내한다. 서버 메시지도 그대로 보여 준다. */
function validate({ title, description }: BroadcastInput): string | null {
  if (!title.trim()) return "방송 제목을 입력해 주세요.";
  if (title.length > TITLE_MAX) return `방송 제목은 ${TITLE_MAX}자 이하로 입력해 주세요.`;
  if (description.length > DESCRIPTION_MAX) return `방송 설명은 ${DESCRIPTION_MAX}자 이하로 입력해 주세요.`;
  return null;
}

/**
 * 방송 등록 · 수정 공통 폼. onSubmit이 끝나면 호출한 쪽이 다른 화면으로 이동한다.
 * cacheComponents는 떠난 화면을 숨겨 두기만 하므로, 저장에 성공한 폼은 숨겨질 때 처음 값으로 되돌린다.
 */
export function BroadcastForm({
  initial = EMPTY,
  submitLabel,
  pendingLabel,
  cancelHref,
  onSubmit,
}: {
  initial?: BroadcastInput;
  submitLabel: string;
  pendingLabel: string;
  cancelHref: string;
  onSubmit: (input: BroadcastInput) => Promise<void>;
}) {
  const { refresh } = useAuth();
  const [values, setValues] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const submitted = useRef(false);
  const initialRef = useRef(initial);

  useLayoutEffect(() => {
    const resetTo = initialRef.current;
    return () => {
      if (!submitted.current) return;
      submitted.current = false;
      setValues(resetTo);
      setPending(false);
    };
  }, []);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const invalid = validate(values);
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    setPending(true);
    try {
      await onSubmit(values);
      submitted.current = true;
    } catch (e) {
      setPending(false);
      if (e instanceof ApiError && e.status === 401) {
        await refresh();
      }
      setError(e instanceof ApiError ? e.message : "저장하지 못했습니다.");
    }
  };

  const set = (key: keyof BroadcastInput) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setValues((v) => ({ ...v, [key]: event.target.value }));

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      <div className="flex flex-col gap-1.5">
        <div className="flex items-baseline justify-between">
          <label htmlFor="title" className="text-sm font-medium">
            방송 제목 <span className="text-live">*</span>
          </label>
          <Counter length={values.title.length} max={TITLE_MAX} />
        </div>
        <input
          id="title"
          name="title"
          value={values.title}
          onChange={set("title")}
          maxLength={TITLE_MAX}
          required
          autoFocus
          placeholder="예) 저녁 코딩 라이브"
          className="h-11 rounded-lg border border-line bg-surface-2 px-3 text-base outline-none placeholder:text-muted/70 focus:border-live"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <div className="flex items-baseline justify-between">
          <label htmlFor="description" className="text-sm font-medium">
            방송 설명 <span className="text-xs font-normal text-muted">(선택)</span>
          </label>
          <Counter length={values.description.length} max={DESCRIPTION_MAX} />
        </div>
        <textarea
          id="description"
          name="description"
          value={values.description}
          onChange={set("description")}
          maxLength={DESCRIPTION_MAX}
          rows={6}
          placeholder="시청자에게 보여 줄 소개를 적어 주세요."
          className="resize-y rounded-lg border border-line bg-surface-2 px-3 py-2.5 text-base leading-relaxed outline-none placeholder:text-muted/70 focus:border-live"
        />
      </div>

      <ErrorMessage message={error} />

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Link href={cancelHref} className="flex h-11 items-center justify-center rounded-lg px-4 text-sm text-muted hover:bg-surface-2 hover:text-fg">
          취소
        </Link>
        <PrimaryButton type="submit" disabled={pending}>
          {pending ? pendingLabel : submitLabel}
        </PrimaryButton>
      </div>
    </form>
  );
}

function Counter({ length, max }: { length: number; max: number }) {
  return (
    <span className={`text-xs tabular-nums ${length >= max ? "text-live" : "text-muted"}`}>
      {length}/{max}
    </span>
  );
}
