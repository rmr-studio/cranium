import { render, screen, fireEvent } from '@testing-library/react';
import { TruncatedChipList, ChipItem } from './truncated-chip-list';

jest.mock('@/components/ui/icon/icon-cell', () => ({
  IconCell: ({ type, colour }: { type: string; colour: string }) => (
    <span data-testid="icon-cell" data-type={type} data-colour={colour} />
  ),
}));

const mockItems: ChipItem[] = [
  { id: '1', label: 'Note 1', subtitle: '2h ago' },
  { id: '2', label: 'Note 2', subtitle: '1d ago' },
  { id: '3', label: 'Note 3', subtitle: '3d ago' },
  { id: '4', label: 'Note 4', subtitle: '1w ago' },
  { id: '5', label: 'Note 5', subtitle: '2w ago' },
];

describe('TruncatedChipList', () => {
  it('renders all items when count <= maxVisible', () => {
    render(<TruncatedChipList items={mockItems.slice(0, 3)} maxVisible={3} />);
    expect(screen.getByText('Note 1')).toBeInTheDocument();
    expect(screen.getByText('Note 2')).toBeInTheDocument();
    expect(screen.getByText('Note 3')).toBeInTheDocument();
    expect(screen.queryByText(/more/)).not.toBeInTheDocument();
  });

  it('truncates and shows "+X more" when count > maxVisible', () => {
    render(<TruncatedChipList items={mockItems} maxVisible={3} />);
    expect(screen.getByText('Note 1')).toBeInTheDocument();
    expect(screen.getByText('Note 2')).toBeInTheDocument();
    expect(screen.getByText('Note 3')).toBeInTheDocument();
    expect(screen.queryByText('Note 4')).not.toBeInTheDocument();
    expect(screen.getByText('+2 more')).toBeInTheDocument();
  });

  it('calls onChipClick with correct item', () => {
    const onClick = jest.fn();
    render(
      <TruncatedChipList items={mockItems.slice(0, 2)} onChipClick={onClick} />
    );
    fireEvent.click(screen.getByText('Note 1'));
    expect(onClick).toHaveBeenCalledWith(mockItems[0]);
  });

  it('calls onOverflowClick when "+X more" is clicked', () => {
    const onOverflow = jest.fn();
    render(
      <TruncatedChipList
        items={mockItems}
        maxVisible={2}
        onOverflowClick={onOverflow}
      />
    );
    fireEvent.click(screen.getByText('+3 more'));
    expect(onOverflow).toHaveBeenCalled();
  });

  it('renders emptyState when items is empty', () => {
    render(
      <TruncatedChipList items={[]} emptyState={<span>No items</span>} />
    );
    expect(screen.getByText('No items')).toBeInTheDocument();
  });

  it('renders nothing when items is empty and no emptyState', () => {
    const { container } = render(<TruncatedChipList items={[]} />);
    expect(container.firstChild).toBeNull();
  });
});
