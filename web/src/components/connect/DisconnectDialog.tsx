"use client";

import { useId, useLayoutEffect, useRef, useState } from "react";
import { fieldError, primaryButton, secondaryButton } from "@/components/connect/styles";
import { ApiError } from "@/lib/api/client";
import { disconnectToss } from "@/lib/api/account-client";
import { refreshMe } from "@/lib/use-me";

/**
 * 연결 해제 확인. 네이티브 <dialog>(showModal)라 초점 가두기·Esc 닫기·배경 비활성화를 브라우저가 맡는다.
 * 닫히면 여는 버튼으로 초점이 돌아간다.
 */
export function DisconnectButton({ maskedAccountNo }: { maskedAccountNo: string | null }) {
  const id = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const openerRef = useRef<HTMLButtonElement>(null);
  const [deleteData, setDeleteData] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 다른 화면으로 떠나면(Activity) 열린 대화상자를 닫는다.
  useLayoutEffect(() => {
    const dialog = dialogRef.current;
    return () => dialog?.close();
  }, []);

  function open() {
    setDeleteData(false);
    setError(null);
    dialogRef.current?.showModal();
  }

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await disconnectToss(deleteData);
      await refreshMe();
      // 세션·계좌가 바뀌었으니 숨겨 둔 화면(Activity)의 이전 계좌 데이터까지 버리도록 전체 이동한다.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- 의도한 전체 새로고침
      window.location.assign("/");
    } catch (e) {
      setBusy(false);
      setError(e instanceof ApiError ? e.message : "연결을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  }

  return (
    <>
      <button ref={openerRef} type="button" onClick={open} className={`${secondaryButton} w-full sm:w-auto`}>
        연결 해제
      </button>
      <dialog
        ref={dialogRef}
        aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-desc`}
        onClose={() => openerRef.current?.focus()}
        onCancel={(e) => {
          if (busy) e.preventDefault();
        }}
        className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl border border-border bg-background p-0 text-foreground backdrop:bg-background/80"
      >
        <div className="space-y-4 p-5">
          <h2 id={`${id}-title`} className="text-lg font-semibold">
            토스증권 연결을 해제할까요?
          </h2>
          <div id={`${id}-desc`} className="space-y-2 text-sm text-muted break-keep">
            <p>
              {maskedAccountNo ? <span className="font-mono text-foreground">{maskedAccountNo}</span> : "이 계좌"}의 저장된 키를 바로 지우고, 더 이상 새로
              기록을 모으지 않아요.
            </p>
            <p>토스증권 WTS의 Open API 화면에서도 키를 삭제하면 더 안전해요.</p>
          </div>
          <label className="flex items-start gap-3 rounded-xl border border-border p-4 text-sm">
            <input
              type="checkbox"
              checked={deleteData}
              onChange={(e) => setDeleteData(e.target.checked)}
              aria-describedby={`${id}-data-hint`}
              className="mt-0.5 size-4 shrink-0 accent-accent"
            />
            <span>
              <span className="block font-medium">계좌 기록도 함께 삭제</span>
              <span id={`${id}-data-hint`} className="mt-1 block text-muted break-keep">
                {deleteData ? "지금까지 쌓인 로그와 체결 기록을 모두 지워요. 되돌릴 수 없어요." : "체크하지 않으면 지금까지 쌓인 로그는 남겨 둬요."}
              </span>
            </span>
          </label>
          <p aria-live="assertive" className={error ? fieldError : "sr-only"}>
            {error ?? (busy ? "연결을 해제하고 있어요." : "")}
          </p>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <button type="button" autoFocus onClick={() => dialogRef.current?.close()} disabled={busy} className={secondaryButton}>
              취소
            </button>
            <button type="button" onClick={confirm} disabled={busy} className={primaryButton}>
              {busy ? "해제하는 중…" : deleteData ? "해제하고 기록 삭제" : "연결 해제"}
            </button>
          </div>
        </div>
      </dialog>
    </>
  );
}
