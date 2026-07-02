import ReactQuill from "react-quill-new";
import "react-quill-new/dist/quill.snow.css";

// antd Form.Item injects `value` + `onChange`; Quill's onChange emits the HTML
// string as its first argument, which Form.Item stores as the field value.
// The stored HTML is rendered verbatim on the product page (th:utext).
interface Props {
  value?: string;
  onChange?: (html: string) => void;
}

const MODULES = {
  toolbar: [
    [{ header: [2, 3, false] }],
    ["bold", "italic", "underline"],
    [{ list: "ordered" }, { list: "bullet" }],
    ["link", "clean"],
  ],
};

export const RichTextEditor = ({ value, onChange }: Props) => (
  <ReactQuill theme="snow" value={value ?? ""} onChange={onChange} modules={MODULES} />
);
