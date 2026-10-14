-- Inline-editor settings applied after the pinned native profile is loaded.
NvimInput = {}

function NvimInput.middle(text, width)
  if vim.fn.strdisplaywidth(text) <= width then return text end
  local head = math.ceil((width - 3) / 2)
  local tail = math.floor((width - 3) / 2)
  local chars = vim.fn.strchars(text)
  while vim.fn.strdisplaywidth(vim.fn.strcharpart(text, 0, head)) > math.ceil((width - 3) / 2) do
    head = head - 1
  end
  while vim.fn.strdisplaywidth(vim.fn.strcharpart(text, chars - tail, tail)) > math.floor((width - 3) / 2) do
    tail = tail - 1
  end
  return vim.fn.strcharpart(text, 0, head) .. '...' .. vim.fn.strcharpart(text, chars - tail, tail)
end

function NvimInput.filename()
  local width = vim.api.nvim_win_get_width(0)
  -- Reserve the actual display width of airline's other sections and flags.
  -- Counting even sections hidden in narrow windows leaves a safe margin.
  local reserved = 6 + (vim.bo.modified and 3 or 0) + (vim.bo.readonly and 4 or 0)
  for _, section in ipairs({'a', 'b', 'y', 'z', 'warning', 'error', 'warning2'}) do
    local format = vim.g['airline_section_' .. section] or ''
    local rendered = vim.api.nvim_eval_statusline(format, {winid = 0, maxwidth = width})
    if rendered.width > 0 then reserved = reserved + rendered.width + 2 end
  end
  return NvimInput.middle(vim.fn.expand('%:t'), math.max(7, width - reserved))
end

vim.g.airline_section_c = '%{v:lua.NvimInput.filename()}%m %r'
vim.g.airline_section_x = ''
vim.cmd('AirlineRefresh')

function NvimInput.wheel(down)
  local win = vim.fn.getmousepos().winid
  if win == 0 or not vim.api.nvim_win_is_valid(win) then return end
  vim.api.nvim_win_call(win, function()
    if down and vim.fn.line('w$') >= vim.fn.line('$') then return end
    local key = vim.api.nvim_replace_termcodes(down and '<C-e>' or '<C-y>', true, false, true)
    vim.cmd.normal({args = {key}, bang = true})
  end)
end

-- Native mappings retain insert/visual mode and target the window under the mouse.
vim.keymap.set({'n', 'i', 'v'}, '<ScrollWheelDown>', function() NvimInput.wheel(true) end)
vim.keymap.set({'n', 'i', 'v'}, '<ScrollWheelUp>', function() NvimInput.wheel(false) end)
