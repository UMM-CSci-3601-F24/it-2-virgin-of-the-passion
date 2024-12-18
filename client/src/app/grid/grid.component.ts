import { Component, ElementRef, inject, Renderer2 } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatGridListModule } from '@angular/material/grid-list';
import { CommonModule } from '@angular/common';
import { GridCell } from '../grid-cell/grid-cell';
import { GridCellComponent } from '../grid-cell/grid-cell.component';
import { GridService } from './grid.service';
import { GridPackage } from './gridPackage';
// import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
// import { catchError, map, of, switchMap } from 'rxjs';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { WebSocketService } from '../web-socket.service';
// import { Grid } from './grid';

@Component({
  selector: 'app-grid-component',
  templateUrl: 'grid.component.html',
  styleUrls: ['./grid.component.scss'],
  standalone: true,
  providers: [],
  imports: [
    MatFormFieldModule,
    MatInputModule,
    FormsModule,
    CommonModule,
    GridCellComponent,
    MatGridListModule,
    RouterLink,
    MatButtonModule,
  ],
})
export class GridComponent {

  gridHeight: number = 10;
  gridWidth: number = 10;
  cellSize: number = 40;

  gridPackage: GridPackage = {
    grid: [],
    _id: '',
    owner: 'currentUser'
  }

  savedGrids: GridPackage[];

  currentRow: number = 0;
  currentCol: number = 0;
  typeDirection: string = "right"; // Current direction
  typingDirections: string[] = ["right", "left", "up", "down"]; // Possible Directions
  currentDirectionIndex: number = 0;

  private focusTimeout: ReturnType<typeof setTimeout>;
  private route = inject(ActivatedRoute);

  constructor(private renderer: Renderer2, public elRef: ElementRef, private gridService: GridService, private webSocketService: WebSocketService) {
    this.initializeGrid();
    this.loadSavedGrids();
    this.webSocketService.getMessage().subscribe((message: unknown) => {
      const msg = message as { type: string, grid: GridCell[][], id: string};
      if (msg.type === 'GRID_UPDATE' && this.gridPackage._id == (message as { id: string }).id) {
        this.applyGridUpdate(msg.grid);
        // this.applyGridUpdate((message as { grid: GridCell[][] }).grid);
      }
    });
  }

  private applyGridUpdate(grid: GridCell[][]) {
    this.gridPackage.grid = grid;
    this.gridHeight = grid.length;
    this.gridWidth = grid[0].length;
  }

  /**
   * Handles the input size change event.
   * Reinitializes the grid based on the new size.
   */
  onSizeInput() {
    console.log(this.gridWidth);
    console.log(this.gridHeight);
    this.initializeGrid();
  }

  /**
   * Handles the input size change event.
   * Reinitializes the grid based on the new size.
   */
  initializeGrid() {
    this.gridPackage.grid=[];
      for(let row=0; row<this.gridHeight; ++row) {
        this.gridPackage.grid.push([]);
        for(let col=0; col<this.gridWidth; ++col) {
          this.gridPackage.grid[row].push(new GridCell());
    }
   }
  }

  saveGrid() {
    if (this.gridPackage._id !== null && this.gridPackage._id !== ''){
      const gridData: Partial<GridPackage> = {
        owner: this.gridPackage.owner,
        grid: this.gridPackage.grid,
        _id: this.gridPackage._id
      };
      this.gridService.saveGrid(gridData).subscribe(() => {
        this.loadSavedGrids();
      });
    } else {
      const gridData: Partial<GridPackage> = {
        owner: this.gridPackage.owner,
        grid: this.gridPackage.grid
      };
      this.gridService.saveGrid(gridData).subscribe(() => {
        this.loadSavedGrids();
      });
    }
  }

  loadSavedGrids() {
    this.gridService.getGrids().subscribe(grids => {
      this.savedGrids = grids;
    });
  }

  loadGrid(id: string) {
    this.gridService.getGridById(id).subscribe(
      (activeGrid) => {
        console.log(activeGrid._id);

        this.gridPackage._id = activeGrid._id;
        this.gridPackage.owner = activeGrid.owner;
        this.applyGridUpdate(activeGrid.grid);
      },
    );
  }

  onGridChange() {
    const message = {
      type: 'GRID_UPDATE',
      grid: this.gridPackage.grid,
      owner: this.gridPackage.owner,
      id: this.gridPackage._id

    };
    console.log(message);
    this.webSocketService.sendMessage(message);
  }

  /**
   * Handles the click event on a grid cell.
   * Moves the focus to the clicked cell.
   *
   * @param event - The mouse event.
   * @param col - The column index of the clicked cell.
   * @param row - The row index of the clicked cell.
   */

  onClick(event: MouseEvent, col: number, row: number) {
    this.moveFocus(col, row);
  }

  /**
   * Handles the keydown event on a grid cell.
   * Moves the focus or modifies the cell value based on the key pressed.
   *
   * @param event - The keyboard event.
   * @param col - The column index of the focused cell.
   * @param row - The row index of the focused cell.
   */
  onKeydown(event: KeyboardEvent, col: number, row: number) {
    const cell = this.gridPackage.grid[row][col];
    const inputElement = this.elRef.nativeElement.querySelector(`app-grid-cell[col="${col}"][row="${row}"] input`);

    console.log('keydown', event.key, col, row);

    if (this.focusTimeout) {
      clearTimeout(this.focusTimeout);
    }

    this.focusTimeout = setTimeout(() => { // Look into debounce, probably a better solution than timeout
    if (!event.ctrlKey) {
      switch (event.key) {
          case 'ArrowUp':
            this.moveFocus(col, row - 1);
            break;
          case 'ArrowDown':
            this.moveFocus(col, row + 1);
            break;
          case 'ArrowLeft':
            this.moveFocus(col - 1, row);
            break;
          case 'ArrowRight':
            this.moveFocus(col + 1, row);
            break;
          case 'Backspace':
            if (inputElement) {
              cell.value = '';
            }
            if (this.typeDirection === "right") {
              if (cell.edges.left === false) {
                this.moveFocus(col - 1, row)
              }
            }
            if (this.typeDirection === "left") {
              if (cell.edges.right === false) {
                this.moveFocus(col + 1, row)
              }
            }
            if (this.typeDirection === "up") {
              if (cell.edges.bottom === false) {
                this.moveFocus(col, row + 1)
              }
            }
            if (this.typeDirection === "down") {
              if (cell.edges.top === false) {
                this.moveFocus(col, row - 1)
               }
            }
            break;
          default:
            if (event.key.length === 1 && event.key.match(/[a-zA-Z]/)) {

              cell.value = event.key;

              if (this.typeDirection === "right") {
                if (cell.edges.right === false) {
                  this.moveFocus(col + 1, row)
                }
              }
              if (this.typeDirection === "left") {
                if (cell.edges.left === false) {
                  this.moveFocus(col - 1, row)
                }
              }
              if (this.typeDirection === "up") {
                if (cell.edges.top === false) {
                 this.moveFocus(col, row - 1)
                }
              }
              if (this.typeDirection === "down") {
                if (cell.edges.bottom === false) {
                  this.moveFocus(col, row + 1)
                }
              }
            }
            break;
        }
      } else{
          switch (event.key) {
            case 'Backspace':
            if (inputElement) {
              console.log(inputElement.value);
              this.renderer.setProperty(inputElement, 'value', '');
              setTimeout(() => this.moveFocus(col, row), 0);
              console.log(inputElement.value);
            }
        }
      }
    }, );
  }

  /**
   * Moves the focus to the specified cell.
   *
   * @param col - The column index of the target cell.
   * @param row - The row index of the target cell.
   */
  moveFocus(col: number, row: number) {
    if (this.gridPackage.grid[row] != undefined && col >= 0 && col < this.gridPackage.grid[row].length && row >= 0 && row < this.gridPackage.grid.length) {
      this.currentCol = col;
      this.currentRow = row;

      console.log(col, row);

      const cellInput = document.querySelector(`app-grid-cell[col="${col}"][row="${row}"] input`);
      console.log(cellInput);

      if (cellInput) {
        setTimeout(() => (cellInput as HTMLElement).focus());
      }
    }
  }


  /**
   * Cycles through the typing directions.
   * Updates the current typing direction.
   */
  cycleTypingDirection() {
    this.currentDirectionIndex = (this.currentDirectionIndex + 1) % this.typingDirections.length;
    this.typeDirection = this.typingDirections[this.currentDirectionIndex];
    console.log(`Typing direction changed to: ${this.typeDirection}`);
  }

  // saveGrid() {
  //   this.gridService.saveGrid(this.grid);
  // }
}
