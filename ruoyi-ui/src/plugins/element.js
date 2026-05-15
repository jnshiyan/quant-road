import Vue from 'vue'
import Cookies from 'js-cookie'

import Breadcrumb from 'element-ui/lib/breadcrumb'
import BreadcrumbItem from 'element-ui/lib/breadcrumb-item'
import Button from 'element-ui/lib/button'
import Card from 'element-ui/lib/card'
import Checkbox from 'element-ui/lib/checkbox'
import CheckboxButton from 'element-ui/lib/checkbox-button'
import Col from 'element-ui/lib/col'
import DatePicker from 'element-ui/lib/date-picker'
import Dialog from 'element-ui/lib/dialog'
import Dropdown from 'element-ui/lib/dropdown'
import DropdownItem from 'element-ui/lib/dropdown-item'
import DropdownMenu from 'element-ui/lib/dropdown-menu'
import Empty from 'element-ui/lib/empty'
import Form from 'element-ui/lib/form'
import FormItem from 'element-ui/lib/form-item'
import Input from 'element-ui/lib/input'
import InputNumber from 'element-ui/lib/input-number'
import Menu from 'element-ui/lib/menu'
import MenuItem from 'element-ui/lib/menu-item'
import Option from 'element-ui/lib/option'
import Popover from 'element-ui/lib/popover'
import Radio from 'element-ui/lib/radio'
import RadioButton from 'element-ui/lib/radio-button'
import RadioGroup from 'element-ui/lib/radio-group'
import Row from 'element-ui/lib/row'
import Scrollbar from 'element-ui/lib/scrollbar'
import Select from 'element-ui/lib/select'
import Submenu from 'element-ui/lib/submenu'
import Switch from 'element-ui/lib/switch'
import TabPane from 'element-ui/lib/tab-pane'
import Table from 'element-ui/lib/table'
import TableColumn from 'element-ui/lib/table-column'
import Tabs from 'element-ui/lib/tabs'
import Tag from 'element-ui/lib/tag'
import Tooltip from 'element-ui/lib/tooltip'
import Loading from 'element-ui/lib/loading'
import Message from 'element-ui/lib/message'
import MessageBox from 'element-ui/lib/message-box'
import Notification from 'element-ui/lib/notification'

const components = [
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Card,
  Checkbox,
  CheckboxButton,
  Col,
  DatePicker,
  Dialog,
  Dropdown,
  DropdownItem,
  DropdownMenu,
  Empty,
  Form,
  FormItem,
  Input,
  InputNumber,
  Menu,
  MenuItem,
  Option,
  Popover,
  Radio,
  RadioButton,
  RadioGroup,
  Row,
  Scrollbar,
  Select,
  Submenu,
  Switch,
  TabPane,
  Table,
  TableColumn,
  Tabs,
  Tag,
  Tooltip
]

let installed = false

const element = {
  install(targetVue = Vue) {
    if (installed) {
      return
    }
    installed = true

    components.forEach(component => {
      targetVue.component(component.name, component)
    })

    targetVue.use(Loading.directive)
    targetVue.prototype.$ELEMENT = {
      size: Cookies.get('size') || 'medium'
    }
    targetVue.prototype.$loading = Loading.service
    targetVue.prototype.$msgbox = MessageBox
    targetVue.prototype.$alert = MessageBox.alert
    targetVue.prototype.$confirm = MessageBox.confirm
    targetVue.prototype.$prompt = MessageBox.prompt
    targetVue.prototype.$notify = Notification
    targetVue.prototype.$message = Message
  }
}

export {
  Loading,
  Message,
  MessageBox,
  Notification
}

export default element
