import { useCallback } from "react";
import { useInRouterContext, useNavigate } from "react-router-dom";

/**
 * 다른 화면에 일을 맡기고 그 화면 탭으로 가는 통로(시안 editform·distopen처럼 상세를 닫고 담당 화면으로 이동).
 * 받는 화면이 아직 안 열려 있으면 요청을 하나 남겨 두었다가 열릴 때 넘기고, 열려 있으면 바로 넘긴다.
 */
export function createScreenRequest<T>(screenId: string) {
  type Listener = (value: T) => void;
  let pending: { value: T } | null = null;
  const listeners = new Set<Listener>();

  const request = (value: T) => {
    if (listeners.size === 0) {
      pending = { value };
      return;
    }
    pending = null;
    listeners.forEach((listener) => listener(value));
  };

  /** 받는 화면이 쓴다: 남아 있는 요청을 먼저 받고, 이후 요청을 기다린다. */
  const subscribe = (listener: Listener) => {
    listeners.add(listener);
    if (pending) {
      const { value } = pending;
      pending = null;
      listener(value);
    }
    return () => {
      listeners.delete(listener);
    };
  };

  /** 보내는 화면이 쓴다: 요청을 남기고 받는 화면 탭으로 간다. */
  const useOpen = () => {
    const inRouter = useInRouterContext();
    // 라우터 밖(단위 시험)에서는 이동 없이 요청만 남긴다. 한 화면 안에서 라우터 유무는 바뀌지 않는다.
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const navigate = inRouter ? useNavigate() : null;
    return useCallback(
      (value: T) => {
        request(value);
        navigate?.(`/screens/${screenId}`);
      },
      [navigate],
    );
  };

  return { request, subscribe, useOpen };
}
