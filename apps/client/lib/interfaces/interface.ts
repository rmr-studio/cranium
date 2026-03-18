import { UseQueryResult } from "@tanstack/react-query";
import React from "react";

// FCWC: FunctionComponentWithChildren
export type FCWC<T> = React.FC<React.PropsWithChildren<T>>;

// Propless: Component without props
export type Propless = {};

export interface ChildNodeProps {
    children?: React.ReactNode;
}

export interface ClassNameProps {
    className?: string;
}

export interface SupabaseClientResponse<T extends Error, V = unknown> {
    ok: boolean;
    data?: V;
    error?: T;
}

export interface Query<T extends object> {
    query: UseQueryResult<T>;
    isEnabled?: boolean;
}

export interface ControllerResponse<T> {
    status: number;
    data?: T;
    error?: string;
}

export interface FormFieldProps<T> {
    value: T;
    onChange: (value: T) => void;
    onBlur?: () => void;
}

export interface SidebarGroupProps {
    title?: string;
    collapsible?: boolean;
    actions?: React.ReactNode;
    items: Array<{
        icon: React.ReactNode;
        title: string;
        url: string;
        hidden?: boolean;
        isActive?: boolean;
        skeleton?: boolean;
    }>;
    subgroups?: Array<SidebarGroupProps>;
}

export type AuthenticatedQueryResult<TData, TError = Error> = UseQueryResult<TData, TError> & {
    isLoadingAuth: boolean;
};

export type AuthenticatedMultiQueryResult<TData, TError = Error> = {
    data: TData;
    isError: boolean;
    isLoading: boolean;
    error?: TError;
    isLoadingAuth: boolean;
};

export interface DialogControl {
    open: boolean;
    setOpen: React.Dispatch<React.SetStateAction<boolean>>;
}

export interface InputValidation {
    maxSize: number;
    allowedTypes: string[];
    errorMessage: string;
}
